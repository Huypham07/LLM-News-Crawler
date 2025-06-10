package com.vdt.crawler.fetcher_service.service;

import com.vdt.crawler.fetcher_service.exception.PageBiggerThanMaxSizeException;
import com.vdt.crawler.fetcher_service.metric.FetcherMetrics;
import com.vdt.crawler.fetcher_service.model.Domain;
import com.vdt.crawler.fetcher_service.model.RetryUrlMessage;
import com.vdt.crawler.fetcher_service.model.URLMetaData;
import com.vdt.crawler.fetcher_service.repository.URLRepository;
import com.vdt.crawler.fetcher_service.util.UrlHashUtil;
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

@Service
public class FetcherService {
    private static final Logger logger = LoggerFactory.getLogger(FetcherService.class);

    private final PageFetcher pageFetcher;
    private final KafkaTemplate<String, String> parsingKafkaTemplate;
    private final KafkaTemplate<String, RetryUrlMessage> retryKafkaTemplate;
    private final URLRepository urlRepository;
    private final RedisTemplate<String, Long> redisTemplate;
    private final RestTemplate restTemplate;
    private final FetcherMetrics fetcherMetrics;

    @Autowired
    public FetcherService(PageFetcher pageFetcher, URLRepository urlRepository,
                          @Qualifier("parsingKafkaTemplate")KafkaTemplate<String, String> parsingKafkaTemplate,
                          @Qualifier("retryKafkaTemplate")KafkaTemplate<String, RetryUrlMessage> retryKafkaTemplate,
                          RedisTemplate<String, Long> redisTemplate, RestTemplate restTemplate, FetcherMetrics fetcherMetrics) {
        this.pageFetcher = pageFetcher;
        this.parsingKafkaTemplate = parsingKafkaTemplate;
        this.retryKafkaTemplate = retryKafkaTemplate;
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.fetcherMetrics = fetcherMetrics;
    }

    @Value("${fetcher-service.frontier-hostname:localhost}")
    private String frontierHost;

    public void processUrl(String url) {
        if (url == null || url.isEmpty()) {
            logger.error("url is null or empty");
            return;
        }

        String host;
        String path;
        try {
            URL urlObj = new URL(url);
            host = urlObj.getHost();
            path = urlObj.getPath();
        } catch (MalformedURLException e) {
            logger.error("Malformed URL: {}", url);
            return;
        }

        PageFetchResult result = fetch(url);

        if (result == null) {
            fetcherMetrics.incrementFailedUrls(host);
            return;
        }

        if (!result.getContentType().contains("html")) {
            fetcherMetrics.incrementFailedUrls(host);
            logger.warn("url {} is not html -> drop", url);
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
            fetcherMetrics.incrementFailedUrls(host);
            logger.error("UnsupportedEncodingException", e);
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
            redisTemplate.opsForValue().set("status:" + urlHash, (long) result.getStatusCode(), Duration.ofMinutes(20));
            redisTemplate.opsForValue().set("retry_count:" + urlHash, (long) urlMetaData.getRetryCount(),  Duration.ofMinutes(20));
            retryKafkaTemplate.send("retry_url_tasks", new RetryUrlMessage(url, urlMetaData.getRetryCount(),
                    urlMetaData.getLastAttempt(), urlMetaData.getStatusCode()));
            fetcherMetrics.incrementFailedUrls(host);
            return;
        }

        urlMetaData.setRetryCount(0);
        // if content not change ... return... but now ignore

        // save in DB and Redis
        urlRepository.save(urlMetaData);
        redisTemplate.opsForValue().set("status:" + urlHash, (long) result.getStatusCode(), Duration.ofHours(1));
        redisTemplate.opsForValue().set("retry_count:" + urlHash, (long) urlMetaData.getRetryCount(),  Duration.ofHours(1));


        if (path.isEmpty() || path.equals("/")) {
            parsingKafkaTemplate.send("home_parsing_tasks", content);
            logger.info("sent raw html of url:{} to Parser to explore sitemap of domain", url);
        } else {
            parsingKafkaTemplate.send("parsing_tasks", content);
            logger.info("sent raw html of url:{} to Parser", url);
        }

        fetcherMetrics.incrementFetchedUrls(host);
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
            fetchResult.fetchContent(500 * 1024 * 1024);
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
        String hostHash = UrlHashUtil.generateUrlHash(host);
        String keyFetchCount = "domain_tracker:fetch_count:" + hostHash;
        String keyLastCrawl = "domain_tracker:last_crawl:" + hostHash;

        Long fetchCount = redisTemplate.opsForValue().increment(keyFetchCount, 1);
        Long lastUpdate = redisTemplate.opsForValue().get(keyLastCrawl);
        Instant now = Instant.now();
        long nowMilli = Instant.now().toEpochMilli();

        fetchCount = fetchCount != null ? fetchCount : 1;
        lastUpdate = lastUpdate != null ? lastUpdate: 0L;

        boolean shouldUpdate = fetchCount >= 10 || nowMilli - lastUpdate > Duration.ofMinutes(3).toMillis();

        if (shouldUpdate) {
            logger.info("Trigger domain update for host: {}", host);

            // Gọi API update domain
            try {
                Domain updated = new Domain();
                updated.setDomain(host);
                updated.setLastCrawled(now);

                restTemplate.put("http://" + frontierHost + ":8091/api/domains/" + host, updated);

                // Reset counter và update time
                redisTemplate.opsForValue().set(keyFetchCount, 0L, Duration.ofMinutes(20));
                redisTemplate.opsForValue().set(keyLastCrawl, nowMilli, Duration.ofMinutes(20));
            } catch (Exception e) {
                logger.error("Failed to update domain: {}", host, e);
            }
        }
    }

}
