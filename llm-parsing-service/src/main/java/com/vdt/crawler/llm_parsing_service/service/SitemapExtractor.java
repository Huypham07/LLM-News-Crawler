package com.vdt.crawler.llm_parsing_service.service;

import com.vdt.crawler.llm_parsing_service.model.Domain;
import com.vdt.crawler.llm_parsing_service.util.UrlUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static com.vdt.crawler.llm_parsing_service.util.UrlResolver.resolveUrl;

@Service
public class SitemapExtractor implements Parsing{
    private final Logger logger = LoggerFactory.getLogger(SitemapExtractor.class);
    private final RestTemplate restTemplate;

    @Value("${parsing-service.frontier-hostname:localhost}")
    private String frontierHost;

    @Autowired
    public SitemapExtractor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // CSS selectors for navigation elements finding
    private static final String[] NAV_SELECTORS = {
            "nav", "navigation",
            ".nav", ".navigation", ".navbar", ".menu", ".main-menu", ".primary-menu",
            ".header-menu", ".top-menu", ".site-nav", ".main-nav", ".menu-nav", ".header-nav", ".primary-nav",
            "#nav", "#navigation", "#navbar", "#menu", "#main-menu", "#primary-menu", "#header-menu", "#primary-nav", "#header-nav", "#menu-nav",
            "[role=navigation]", "[role=menubar]",
            ".megamenu", ".dropdown-menu"
    };

    // Selectors for header area (can have nav)
    private static final String[] HEADER_SELECTORS = {
            "header", ".header", ".site-header", ".page-header", ".main-header",
            "#header", "#site-header", "#page-header", "#main-header",
            ".masthead", ".banner", ".top-bar"
    };

    @Override
    public void parse(String rawHtml) {
        List<String> result = getParsingResult(rawHtml);
        doAfterParse(result);
    }

    public void doAfterParse(List<String> result) {
        if (result != null && !result.isEmpty()) {
            String host = extractHostFromUrl(result.get(0));
            if (host != null) {
                Domain updated = new Domain();
                updated.setDomain(host);
                updated.setSeedUrls(result);

                try {
                    restTemplate.put("http://" + frontierHost + ":8091/api/domains/" + host, updated);
                    logger.info("updated seedUrls ({} urls) of {}", result.size(), host);
                    logger.debug("Seed URLs: {}", result);
                } catch (Exception e) {
                    logger.error("Failed to update seedUrls ({} urls) of {}", result.size(), host);
                }
            }
        }
    }

    public List<String> getParsingResult(String rawHtml) {
        List<String> result = new ArrayList<>();
        Set<String> uniqueUrls = new LinkedHashSet<>();

        try {
            Document doc = Jsoup.parse(rawHtml);
            String baseUrl = UrlUtil.extractCurrentUrl(doc);

            if (baseUrl == null) {
                logger.warn("Cannot determine base URL from HTML content");
                return result;
            }

            uniqueUrls.add(normalizeUrl(baseUrl));

            // extract nav element
            extractFromNavigationElements(doc, baseUrl, uniqueUrls);
            // extract header area
            extractFromHeaderArea(doc, baseUrl, uniqueUrls);
            // extract prominent links
//            extractProminentLinks(doc, baseUrl, uniqueUrls);
            // extract category links
//            extractCategoryAndBreadcrumbLinks(doc, baseUrl, uniqueUrls);
            // extract content
//            extractFromMainContentAreas(doc, baseUrl, uniqueUrls);

            result.addAll(uniqueUrls);

            logger.info("Extract {} navigation URLs from HTML content", result.size());
        } catch (Exception e) {
            logger.error("Failed to extract navigation URLs from HTML content: {}", e.getMessage(), e);
        }

        return result;
    }

    public List<String> getParsingResult(Document doc) {
        List<String> result = new ArrayList<>();
        Set<String> uniqueUrls = new LinkedHashSet<>();

        try {
            String baseUrl = UrlUtil.extractCurrentUrl(doc);

            if (baseUrl == null) {
                logger.warn("Cannot determine base URL from HTML content");
                return result;
            }

            uniqueUrls.add(normalizeUrl(baseUrl));

            // extract nav element
            extractFromNavigationElements(doc, baseUrl, uniqueUrls);
            // extract header area
            extractFromHeaderArea(doc, baseUrl, uniqueUrls);
            // extract prominent links
//            extractProminentLinks(doc, baseUrl, uniqueUrls);
            // extract category links
//            extractCategoryAndBreadcrumbLinks(doc, baseUrl, uniqueUrls);
            // extract content
//            extractFromMainContentAreas(doc, baseUrl, uniqueUrls);

            result.addAll(uniqueUrls);

            logger.info("Extract {} navigation URLs from HTML content", result.size());
        } catch (Exception e) {
            logger.error("Failed to extract navigation URLs from HTML content: {}", e.getMessage(), e);
        }

        return result;
    }



    private void extractFromNavigationElements(Document doc, String baseUrl, Set<String> urls) {
        for (String selector: NAV_SELECTORS) {
            Elements navElements = doc.select(selector);
            for (Element navElement : navElements) {
                Elements links = navElement.select("a[href]");
                for (Element link : links) {
                    String url = processNavigationLink(link, baseUrl);
                    if (url != null) {
                        urls.add(normalizeUrl(url));
                    }
                }
            }
        }
    }

    private void extractFromHeaderArea(Document doc, String baseUrl, Set<String> urls) {
        for (String selector: HEADER_SELECTORS) {
            Elements headerElements = doc.select(selector);
            for (Element headerElement : headerElements) {
                Elements links = headerElement.select("a[href]");
                for (Element link : links) {
                    if (isLikelyNavigationLink(link)) {
                        String url = processNavigationLink(link, baseUrl);
                        if (url != null) {
                            urls.add(normalizeUrl(url));
                        }
                    }

                }
            }
        }
    }

    private void extractFromMainContentAreas(Document doc, String baseUrl, Set<String> urls) {
        Elements contentAreas = doc.select(
                ".main-content, .content, .site-content, #content, #main-content, " +
                        ".category-list, .section-list, .menu-categories, .topics"
        );
        for (Element contentArea : contentAreas) {
            Elements links = contentArea.select("a[href]");
            for (Element link : links) {
                if (isLikelyNavigationLink(link)) {
                    String url = processNavigationLink(link, baseUrl);
                    if (url != null) {
                        urls.add(normalizeUrl(url));
                    }
                }
            }
        }
    }
    private void extractProminentLinks(Document doc, String baseUrl, Set<String> urls) {
        Elements prominentLinks = doc.select(
                "a.btn, a.button, a[class*=prominent], a[class*=primary], a[class*=main], " +
                        "a[class*=category], a[class*=section], a[class*=topic]"
        );

        for (Element link : prominentLinks) {
            String url = processNavigationLink(link, baseUrl);
            if (url != null && isLikelyNavigationUrl(url)) {
                urls.add(normalizeUrl(url));
            }
        }
    }

    private void extractCategoryAndBreadcrumbLinks(Document doc, String baseUrl, Set<String> urls) {
        Elements breadcrumbLinks = doc.select(
                ".breadcrumb a, .breadcrumbs a, [class*=breadcrumb] a, " +
                        ".crumb a, .crumbs a, nav[aria-label*=breadcrumb] a"
        );

        for (Element link : breadcrumbLinks) {
            String url = processNavigationLink(link, baseUrl);
            if (url != null) {
                urls.add(normalizeUrl(url));
            }
        }

        // Category/tag links that appear multiple times (likely important)
        Map<String, Integer> linkFrequency = new HashMap<>();
        Elements allLinks = doc.select("a[href]");

        for (Element link : allLinks) {
            String href = link.attr("href");
            String fullUrl = resolveUrl(baseUrl, href);
            if (isValidNavigationUrl(fullUrl)) {
                linkFrequency.put(normalizeUrl(fullUrl), linkFrequency.getOrDefault(fullUrl, 0) + 1);
            }
        }

        // Add frequently appearing links (likely category/section links)
        for (Map.Entry<String, Integer> entry : linkFrequency.entrySet()) {
            if (entry.getValue() >= 2 && isShortPath(entry.getKey())) {
                urls.add(entry.getKey());
            }
        }
    }

    private String processNavigationLink(Element link, String baseUrl) {
        String href = link.attr("href");

        // Skip empty, javascript, or anchor links
        if (href.isEmpty() || href.startsWith("javascript:") || href.equals("#")) {
            return null;
        }

        String fullUrl = resolveUrl(baseUrl, href);

        if (isValidNavigationUrl(fullUrl)) {
            return fullUrl;
        }

        return null;
    }

    private boolean isLikelyNavigationLink(Element link) {
        String className = link.attr("class").toLowerCase();
        String id = link.attr("id").toLowerCase();

        // Check for navigation-related classes/ids
        if (className.contains("nav") || className.contains("menu") || className.contains("primary") ||
                className.contains("main") || className.contains("category") || className.contains("section") ||
                id.contains("nav") || id.contains("menu")) {
            return true;
        }

        // Check parent elements for navigation context
        Element parent = link.parent();
        while (parent != null && !parent.tagName().equals("body")) {
            String parentClass = parent.attr("class").toLowerCase();
            String parentId = parent.attr("id").toLowerCase();

            if (parentClass.contains("nav") || parentClass.contains("menu") ||
                    parentClass.contains("header") || parentId.contains("nav") ||
                    parentId.contains("menu") || parentId.contains("header")) {
                return true;
            }
            parent = parent.parent();
        }

        return false;
    }

    private boolean isLikelyNavigationUrl(String url) {
        // Skip article/post links (usually have dates or complex paths)
        if (url.matches(".*/\\d{4}/\\d{2}/.*") || url.matches(".*/(post|article|story)/.*")) {
            return false;
        }

        // Prefer shorter paths that look like categories/sections
        try {
            URL urlObj = new URL(url);
            String path = urlObj.getPath();
            return path.length() > 1 && path.length() < 50 && path.split("/").length <= 3;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidNavigationUrl(String url) {
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
            if (EXCLUDE_PATTERN.matcher(url).matches() || FILE_EXTENSION_PATTERN.matcher(url).matches() || ARTICLE_URL_PATTERN.matcher(url).matches()) {
                return false;
            }

            // Skip URLs with complex query parameters
            String query = urlObj.getQuery();
            return query == null || (query.length() <= 20 && !query.contains("&"));

        } catch (MalformedURLException e) {
            return false;
        }
    }

    private boolean isShortPath(String url) {
        try {
            URL urlObj = new URL(url);
            String path = urlObj.getPath();
            return path.length() > 1 && path.length() < 50 && path.split("/").length <= 3;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private String extractHostFromUrl(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (MalformedURLException e) {
            logger.error("Invalid URL format: {}", url);
            return null;
        }
    }

    private String normalizeUrl(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
