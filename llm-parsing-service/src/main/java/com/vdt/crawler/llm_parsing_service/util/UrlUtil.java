package com.vdt.crawler.llm_parsing_service.util;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class UrlUtil {
    public static String extractCurrentUrl(Document doc) {
        // get from canonnical URL
        Element canonical = doc.selectFirst("link[rel=canonical]");
        if (canonical != null) {
            String canonicalUrl = canonical.attr("href");
            return canonicalUrl;
        }

        // get from open graph URL
        Element og = doc.selectFirst("meta[property=og:url]");
        if (og != null) {
            String ogUrl = og.attr("content");
            return ogUrl;
        }

        // base tag
        Element base = doc.selectFirst("base[href]");
        if (base != null) {
            String baseUrl = base.attr("href");
        }

        return null;
    }
}
