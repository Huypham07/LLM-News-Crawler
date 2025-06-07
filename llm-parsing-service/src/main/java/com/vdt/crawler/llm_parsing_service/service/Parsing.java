package com.vdt.crawler.llm_parsing_service.service;

import java.util.regex.Pattern;

public interface Parsing {
    int CONTENT = 1;
    int SITEMAP = 2;
    int URL = 3;

    // File extension
    Pattern FILE_EXTENSION_PATTERN = Pattern.compile(
            ".*\\.(jpg|jpeg|png|gif|pdf|doc|docx|xls|xlsx|zip|rar|mp3|mp4|avi)$",
            Pattern.CASE_INSENSITIVE
    );

    // Unexpected URL
    Pattern EXCLUDE_PATTERN = Pattern.compile(
            ".*(login|register|logout|signin|signup|admin|dashboard|api|rss|feed|search|tag|archive|" +
                    "\\d{4}/\\d{2}/\\d{2}|sitemap\\.xml|robots\\.txt|contact|about|privacy|terms|policy|404|error|" +
                    "ajax|json|xml|pdf|print|share|comment|reply|download|upload|edit|delete|create|update|" +
                    "wp-admin|wp-content|wp-includes|node_modules|\\.git|favicon).*",
            Pattern.CASE_INSENSITIVE
    );

    Pattern ARTICLE_URL_PATTERN = Pattern.compile(
            ".*(\\d{10,}|\\d{4}-\\d{2}-\\d{2}|[a-z0-9-]{50,})(\\.(htm[l]?|tpo|php|aspx|epi))?$|" +  // Long ID or long slug
                    ".*-\\d{12,}[^/]*$|.*-\\d{4}\\d{2}\\d{2}[^/]*$" + // Ending with long timestamp
                    "|.*-[a-z]?\\d{3,}(\\.(htm[l]?|tpo|php|aspx|epi))?$",
            Pattern.CASE_INSENSITIVE
    );


    void parse(String rawHtml);
}
