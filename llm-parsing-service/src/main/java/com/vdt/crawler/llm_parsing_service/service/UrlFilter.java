package com.vdt.crawler.llm_parsing_service.service;

import com.vdt.crawler.llm_parsing_service.model.URLMetaData;
import com.vdt.crawler.llm_parsing_service.repository.URLRepository;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.vdt.crawler.llm_parsing_service.util.UrlHashUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class UrlFilter {
    private final Logger logger = LoggerFactory.getLogger(UrlFilter.class);
    private final URLRepository urlRepository;
    private final RedisTemplate<String, Long> redisTemplate;

    @Autowired
    public UrlFilter(URLRepository urlRepository, RedisTemplate<String, Long> redisTemplate) {
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
    }

    public boolean allow(String url) {
        if (url == null) return false;
        try {
            String urlHash = UrlHashUtil.generateUrlHash(url);
            URL urlObj = new URL(url);
            URLMetaData urlMetaData = null;

            Long status = redisTemplate.opsForValue().get("status:" + urlHash);
            if (status == null) {
                Optional<URLMetaData> urlMetaDataOptional = urlRepository.findById(urlHash);
                if (urlMetaDataOptional.isPresent()) {
                    urlMetaData = urlMetaDataOptional.get();
                    status = Long.valueOf(urlMetaData.getStatusCode());
                }
            }

            if (status == null || urlMetaData == null) {
                logger.info("Url not crawl before {}", url);
                return true;
            }

            if (status != 200 && urlMetaData.getRetryCount() > 3) {
                // < 5mins
                return Instant.now().toEpochMilli() - urlMetaData.getLastAttempt().toEpochMilli() >= 5 * 60 * 1000;
            }

            return !isLikelyArticleByUrl(url);
            // if have any strategy to determine which url is article, will not re-crawl artile if status = ok,
        } catch (MalformedURLException e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    public boolean isLikelyArticleByUrl(String url) {
        return !Parsing.EXCLUDE_PATTERN.matcher(url).matches() && !Parsing.FILE_EXTENSION_PATTERN.matcher(url).matches() && Parsing.ARTICLE_URL_PATTERN.matcher(url).matches();
    }
}
