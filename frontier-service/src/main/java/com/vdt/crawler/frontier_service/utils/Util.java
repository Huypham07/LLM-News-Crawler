package com.vdt.crawler.frontier_service.utils;

public class Util {
    public static boolean hasPlainTextContent(String contentType) {
        String typeStr = (contentType != null) ? contentType.toLowerCase() : "";

        return typeStr.contains("text") && !typeStr.contains("html");
    }
}