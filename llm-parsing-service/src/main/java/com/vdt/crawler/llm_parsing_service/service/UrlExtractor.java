package com.vdt.crawler.llm_parsing_service.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.vdt.crawler.llm_parsing_service.util.UrlResolver.resolveUrl;

@Service
public class UrlExtractor implements Parsing{
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final KafkaTemplate<String, String> newUrlParsingKafkaTemplate;
    private final UrlFilter urlFilter;
    private final SitemapExtractor sitemapExtractor;

    @Autowired
    public UrlExtractor(KafkaTemplate<String, String> newUrlParsingKafkaTemplate, UrlFilter urlFilter, SitemapExtractor sitemapExtractor) {
        this.newUrlParsingKafkaTemplate = newUrlParsingKafkaTemplate;
        this.urlFilter = urlFilter;
        this.sitemapExtractor = sitemapExtractor;
    }

    @Override
    public void parse(String rawHtml) {
        List<String> result = getParsingResult(rawHtml);
        doAfterParse(result);
    }

    public void doAfterParse(List<String> result) {
        if (result != null && !result.isEmpty()) {
            logger.info("Found {} URLs after filtering", result.size());
            for (String url : result) {
                if (urlFilter.allow(url)) {
                    newUrlParsingKafkaTemplate.send("new_url_tasks", url);
                    logger.debug("Sent new URL to Frontier: {}", url);
                } else {
                    logger.debug("URL filtered out by UrlFilter: {}", url);
                }
            }
        } else {
            logger.info("No URLs found to process");
        }
    }

    private List<String> getParsingResult(String rawHtml) {
        try {
            List<String> sitemapUrls = sitemapExtractor.getParsingResult(rawHtml);
            Set<String> sitemapUrlSet = new HashSet<>(sitemapUrls);

            // Extract all URLs from HTML
            Set<String> allUrls = extractUrlsFromHtml(rawHtml);
            logger.debug("Found {} total URLs in HTML", allUrls.size());

            List<String> filteredUrls = allUrls.stream()
                    .filter(url -> !sitemapUrlSet.contains(url)) // Remove sitemap URLs
                    .distinct() // Remove duplicates
                    .collect(Collectors.toList());

            logger.info("Final filtered URL count: {} (removed {} sitemap URLs)",
                    filteredUrls.size(), sitemapUrls.size());

            return filteredUrls;
        } catch (Exception e) {
            logger.error("Error parsing HTML for URLs: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Extract all URLs from HTML content using multiple methods
     */
    private Set<String> extractUrlsFromHtml(String rawHtml) {
        Set<String> urls = new HashSet<>();

        try {
            Document doc = Jsoup.parse(rawHtml);
            String baseUrl = extractBaseUrl(doc);

            if (baseUrl == null) {
                logger.warn("Cannot determine base URL from HTML content");
                return urls;
            }

            // Extract from <a> tags
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String href = link.attr("href");
                if (!href.isEmpty()) {
                    String fullUrl = resolveUrl(baseUrl, href);
                    if (isValidUrl(fullUrl)) {
                        urls.add(normalizeUrl(fullUrl));
                    }
                }
            }

            logger.debug("Extracted {} URLs from HTML", urls.size());

        } catch (Exception e) {
            logger.error("Error extracting URLs: {}", e.getMessage());
        }

        return urls;
    }

    private String extractBaseUrl(Document doc) {
        // get from canonnical URL
        Element canonical = doc.selectFirst("link[rel=canonical]");
        if (canonical != null) {
            String canonicalUrl = canonical.attr("href");
            logger.debug(">>> canonical-url{}", canonicalUrl);
            return canonicalUrl;
        }

        // get from open graph URL
        Element og = doc.selectFirst("meta[property=og:url]");
        if (og != null) {
            String ogUrl = og.attr("content");
            logger.debug(">>> og-url{}", ogUrl);
            return ogUrl;
        }

        // base tag
        Element base = doc.selectFirst("base[href]");
        if (base != null) {
            String baseUrl = base.attr("href");
            logger.debug(">>> base-url{}", baseUrl);
        }

        return null;
    }

    private boolean isValidUrl(String url) {
        try {
            URL urlObj = new URL(url);
            // Basic URL validation
            if (!urlObj.getProtocol().matches("http[s]?") || urlObj.getHost() == null) {
                return false;
            }

            //check have only 1 path segment
            String path = urlObj.getPath(); //  "/xa-hoi/giao-thong.htm"
            String[] segments = path.split("/");

            int nonEmptySegments = 0;
            for (String s : segments) {
                if (!s.isEmpty()) nonEmptySegments++;
            }
            if (nonEmptySegments > 1) {
                return false;
            }

            // Skip unwanted URLs
            if (EXCLUDE_PATTERN.matcher(url).matches() || FILE_EXTENSION_PATTERN.matcher(url).matches()) {
                return false;
            }

            // Skip URLs with complex query parameters
            String query = urlObj.getQuery();
            return query == null || (query.length() <= 20 && !query.contains("&"));

        } catch (MalformedURLException e) {
            return false;
        }
    }

    private String normalizeUrl(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
