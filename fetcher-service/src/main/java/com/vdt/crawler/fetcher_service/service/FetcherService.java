package com.vdt.crawler.fetcher_service.service;

import com.vdt.crawler.fetcher_service.exception.PageBiggerThanMaxSizeException;
import com.vdt.crawler.fetcher_service.model.Domain;
import com.vdt.crawler.fetcher_service.model.RetryUrlMessage;
import com.vdt.crawler.fetcher_service.model.URLMetaData;
import com.vdt.crawler.fetcher_service.repository.URLRepository;
import com.vdt.crawler.fetcher_service.util.UrlHashUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class FetcherService {
    private static final Logger logger = LoggerFactory.getLogger(FetcherService.class);

    private final PageFetcher pageFetcher;
    private final KafkaTemplate<String, String> parsingKafkaTemplate;
    private final KafkaTemplate<String, RetryUrlMessage> retryKafkaTemplate;
    private final URLRepository urlRepository;
    private final RedisTemplate<String, Integer> redisTemplate;
    private final RestTemplate restTemplate;

    @Autowired
    public FetcherService(PageFetcher pageFetcher, URLRepository urlRepository,
                          @Qualifier("parsingKafkaTemplate")KafkaTemplate<String, String> parsingKafkaTemplate,
                          @Qualifier("retryKafkaTemplate")KafkaTemplate<String, RetryUrlMessage> retryKafkaTemplate,
                          RedisTemplate<String, Integer> redisTemplate, RestTemplate restTemplate) {
        this.pageFetcher = pageFetcher;
        this.parsingKafkaTemplate = parsingKafkaTemplate;
        this.retryKafkaTemplate = retryKafkaTemplate;
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    @Value("${fetcher-service.frontier-hostname:localhost}")
    private String frontierHost;

    public void processUrl(String url) {
        if (url == null || url.isEmpty()) {
            logger.error("url is null or empty");
            return;
        }
        PageFetchResult result = fetch(url);

        if (result == null) {
            return;
        }

        String urlHash = UrlHashUtil.generateUrlHash(url);
        String content;
        try {
            if (result.getContentCharset() == null) {
                content = new String(result.getContentData());
            } else {
                content = new String(result.getContentData(), result.getContentCharset());
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("UnsupportedEncodingException", e);
            return;
        }

        String host;
        try {
            host = new URL(url).getHost();
        } catch (MalformedURLException e) {
            logger.error("Malformed URL: {}", url);
            return;
        }

        updateHostFetchStatus(host);

        URLMetaData urlMetaData = urlRepository.findById(urlHash)
                .orElseGet(() -> {
                    URLMetaData newMeta = new URLMetaData();
                    newMeta.setId(urlHash);
                    newMeta.setUrl(url);
                    newMeta.setRawHtml(content);
                    return newMeta;
                });

        urlMetaData.setLastAttempt(Instant.now());
        urlMetaData.setStatusCode(result.getStatusCode());

        if (result.getStatusCode() != HttpStatus.SC_OK) {
            urlMetaData.setRetryCount(urlMetaData.getRetryCount() + 1);
            urlRepository.save(urlMetaData);
            redisTemplate.opsForValue().set("url:" + urlHash, urlMetaData.getStatusCode(), 20, TimeUnit.MINUTES);
            retryKafkaTemplate.send("retry_url", new RetryUrlMessage(url, urlMetaData.getRetryCount(),
                    urlMetaData.getLastAttempt(), urlMetaData.getStatusCode()));
            return;
        }

        urlMetaData.setRetryCount(0);
        // if content not change ... return... but now ignore

        // save in DB and Redis
        urlRepository.save(urlMetaData);
        redisTemplate.opsForValue().set("url:" + urlHash, urlMetaData.getStatusCode(), 1, TimeUnit.HOURS);

        parsingKafkaTemplate.send("parsing_tasks", content);
        logger.debug("sent raw html of url:{} to Parser", url);
    }

    private PageFetchResult fetch(String url) {
        String fetchUrl = url;
        PageFetchResult fetchResult = null;
        try {
            for (int redir = 0; redir < 3; ++redir) {
                fetchResult = pageFetcher.fetchPage(fetchUrl);
                int status = fetchResult.getStatusCode();
                // Follow redirects up to 3 levels
                if ((status == HttpStatus.SC_MULTIPLE_CHOICES ||
                        status == HttpStatus.SC_MOVED_PERMANENTLY ||
                        status == HttpStatus.SC_MOVED_TEMPORARILY ||
                        status == HttpStatus.SC_SEE_OTHER ||
                        status == HttpStatus.SC_TEMPORARY_REDIRECT || status == 308) &&
                        // SC_PERMANENT_REDIRECT RFC7538
                        fetchResult.getMovedToUrl() != null) {
                    fetchUrl = fetchResult.getMovedToUrl();
                    fetchResult.discardContentIfNotConsumed();
                } else {
                    // Done on all other occasions
                    break;
                }
            }

            if (fetchResult.getStatusCode() == HttpStatus.SC_OK) {
                fetchResult.fetchContent(500 * 1024 * 1024);
                if (fetchResult.getContentType()
                        .contains(
                                "html")) {
                    return fetchResult;
                } else {
                    logger.warn(
                            "Can't read content, " + "contentType: {}", fetchResult.getContentType());
                }
            } else {
                logger.debug("Can't access url: {}  as it's status code is {}", fetchUrl, fetchResult.getStatusCode());
            }
        } catch (SocketException | UnknownHostException | SocketTimeoutException |
                 NoHttpResponseException se) {
            logger.trace("Error fetching url: {}", fetchUrl);
        } catch (PageBiggerThanMaxSizeException pbtms) {
            logger.error("Error occurred while fetching url: {}, {}",
                    fetchUrl, pbtms.getMessage());
        } catch (IOException | InterruptedException | RuntimeException e) {
            logger.error("Error occurred while fetching url: " + fetchUrl, e);
        } finally {
            if (fetchResult != null) {
                fetchResult.discardContentIfNotConsumed();
            }
        }
        return fetchResult;
    }

    public void updateHostFetchStatus(String host) {
        String key = "domain_tracker:" + host;

        Long fetchCount = redisTemplate.opsForHash().increment(key, "fetch_count", 1);

        // Lấy thời gian update gần nhất
        String lastUpdateStr = (String) redisTemplate.opsForHash().get(key, "last_update");
        long now = Instant.now().toEpochMilli();
        long lastUpdate = lastUpdateStr != null ? Long.parseLong(lastUpdateStr) : 0L;

        boolean shouldUpdate = fetchCount >= 10 || now - lastUpdate > Duration.ofMinutes(3).toMillis();

        if (shouldUpdate) {
            logger.info("Trigger domain update for host: {}", host);

            // Gọi API update domain
            try {
                Domain updated = new Domain();
                updated.setDomain(host);
                updated.setLastCrawled(Instant.now());

                restTemplate.put("http://" + frontierHost + ":8091/api/domains/" + host, updated);

                // Reset counter và update time
                redisTemplate.opsForHash().put(key, "fetch_count", "0");
                redisTemplate.opsForHash().put(key, "last_update", String.valueOf(now));
            } catch (Exception e) {
                logger.error("Failed to update domain: {}", host, e);
            }
        }
    }

}
