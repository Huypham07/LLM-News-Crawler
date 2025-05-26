package com.vdt.crawler.frontier_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class FrontierConsumer {

    private static final Logger logger = LoggerFactory.getLogger(FrontierConsumer.class);

    private final FrontierService frontierService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    @Autowired
    public FrontierConsumer(FrontierService frontierService) {
        this.frontierService = frontierService;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(10); // Thread pool for parallel processing
    }

    /**
     * Consumer for "new_url" topic
     */
    @KafkaListener(
            topics = "new_url",
            groupId = "frontier-service",
            concurrency = "3"
    )
    public void handleNewUrls(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        logger.debug("Received message from topic: {}, partition: {}, offset: {}", topic, partition, offset);

        try {
            Object urlData = parseUrlMessage(message);

            if (urlData instanceof String) {
                // Single URL
                processUrlAsync((String) urlData, "NEW");
            } else if (urlData instanceof List) {
                // Multiple URLs
                @SuppressWarnings("unchecked")
                List<String> urls = (List<String>) urlData;
                processUrlsAsync(urls, "NEW");
            }

            // Acknowledge message after success
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("Error processing new URL message: {}", message, e);
        }
    }

    /**
     * Consumer for "retry_url" topic
     */
    @KafkaListener(
            topics = "retry_url",
            groupId = "frontier-service",
            concurrency = "2"
    )
    public void handleRetryUrls(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        logger.debug("Received retry message from topic: {}, partition: {}, offset: {}", topic, partition, offset);

        try {
            RetryUrlMessage retryMessage = parseRetryMessage(message);

            if (retryMessage != null && retryMessage.getUrl() != null) {
                // Check retry conditions
                if (shouldRetry(retryMessage)) {
                    processUrlAsync(retryMessage.getUrl(), "RETRY");
                } else {
                    logger.warn("Retry limit exceeded for URL: {}", retryMessage.getUrl());
                }
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("Error processing retry URL message: {}", message, e);
        }
    }

    /**
     * process seed URLs from api
     */
    public void handleSeedUrls(List<String> seedUrls) {
        logger.info("Received {} seed URLs from scheduler", seedUrls.size());

        try {
            processUrlsAsync(seedUrls, "SEED");
        } catch (Exception e) {
            logger.error("Error processing seed URLs", e);
        }
    }

    private Object parseUrlMessage(String message) {
        try {
            // Parse JSON array
            if (message.trim().startsWith("[")) {
                return objectMapper.readValue(message, List.class);
            } else if (message.trim().startsWith("{")) {
                // Parse JSON object
                UrlMessage urlMessage = objectMapper.readValue(message, UrlMessage.class);
                return urlMessage.getUrls() != null ? urlMessage.getUrls() : urlMessage.getUrl();
            } else {
                // Plain text URL
                return message.trim();
            }
        } catch (Exception e) {
            logger.warn("Failed to parse as JSON, treating as plain text: {}", message);
            return message.trim();
        }
    }

    private RetryUrlMessage parseRetryMessage(String message) {
        try {
            return objectMapper.readValue(message, RetryUrlMessage.class);
        } catch (Exception e) {
            logger.warn("Failed to parse retry message as JSON: {}", message);
            // Fallback: treat as simple URL
            RetryUrlMessage retryMessage = new RetryUrlMessage();
            retryMessage.setUrl(message.trim());
            retryMessage.setRetryCount(1);
            return retryMessage;
        }
    }

    private boolean shouldRetry(RetryUrlMessage retryMessage) {
        // Check retry count limit
        if (retryMessage.getRetryCount() > 3) {
            return false;
        }

        // Check if enough time has passed since last attempt
        if (retryMessage.getLastAttempt() != null) {
            long timeSinceLastAttempt = System.currentTimeMillis() - retryMessage.getLastAttempt();
            long minRetryDelay = calculateRetryDelay(retryMessage.getRetryCount());

            if (timeSinceLastAttempt < minRetryDelay) {
                logger.debug("Not enough time passed for retry: {}", retryMessage.getUrl());
                return false;
            }
        }

        return true;
    }

    private long calculateRetryDelay(int retryCount) {
        // Exponential backoff: 1min, 5min, 15min
        switch (retryCount) {
            case 1: return 60 * 1000; // 1 minute
            case 2: return 5 * 60 * 1000; // 5 minutes
            case 3: return 15 * 60 * 1000; // 15 minutes
            default: return 30 * 60 * 1000; // 30 minutes
        }
    }

    private void processUrlAsync(String url, String source) {
        executorService.submit(() -> {
            try {
                logger.debug("Processing {} URL: {}", source, url);
                frontierService.addToFrontier(url);
            } catch (Exception e) {
                logger.error("Error processing URL {}: {}", url, e.getMessage());
            }
        });
    }

    private void processUrlsAsync(List<String> urls, String source) {
        executorService.submit(() -> {
            try {
                logger.debug("Processing {} URLs from {}", urls.size(), source);
                frontierService.addToFrontier(urls);
            } catch (Exception e) {
                logger.error("Error processing URL batch from {}: {}", source, e.getMessage());
            }
        });
    }

    // Message DTOs
    public static class UrlMessage {
        private String url;
        private List<String> urls;
        private String source;
        private Long timestamp;

        // Getters and setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public List<String> getUrls() { return urls; }
        public void setUrls(List<String> urls) { this.urls = urls; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }

    public static class RetryUrlMessage {
        private String url;
        private int retryCount;
        private Long lastAttempt;
        private String errorReason;
        private Integer httpStatus;

        // Getters and setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

        public Long getLastAttempt() { return lastAttempt; }
        public void setLastAttempt(Long lastAttempt) { this.lastAttempt = lastAttempt; }

        public String getErrorReason() { return errorReason; }
        public void setErrorReason(String errorReason) { this.errorReason = errorReason; }

        public Integer getHttpStatus() { return httpStatus; }
        public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    }
}