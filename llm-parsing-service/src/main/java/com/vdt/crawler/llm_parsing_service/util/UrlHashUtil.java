package com.vdt.crawler.llm_parsing_service.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UrlHashUtil {

    private static final String HASH_ALGORITHM = "SHA-256";

    public static String generateUrlHash(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        try {
            // Normalize URL before hashing to ensure consistency
            String normalizedUrl = normalizeUrl(url);

            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(normalizedUrl.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available: " + HASH_ALGORITHM, e);
        }
    }

    /**
     * Normalize URL to ensure consistent hashing
     * @param url the URL to normalize
     * @return normalized URL
     */
    private static String normalizeUrl(String url) {
        String normalized = url.trim().toLowerCase();

        // Remove trailing slash if present (except for root)
        if (normalized.endsWith("/") && normalized.length() > 1 &&
                !normalized.matches("^https?://[^/]+/$")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Ensure protocol is present
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }

        return normalized;
    }
}