package com.vdt.crawler.constants;

public class KafkaTopics {
    public static final String URL_FRONTIER_TOPIC = "url-frontier";
    public static final String URL_PROCESSING_TOPIC = "url-processing";
    public static final String CONTENT_PROCESSING_TOPIC = "content-processing";
    public static final String URL_EXTRACTING_TOPIC = "url-extracting";
    
    private KafkaTopics() {
        // Private constructor to prevent instantiation
    }
} 