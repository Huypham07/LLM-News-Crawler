package com.vdt.crawler.frontier_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vdt.crawler.frontier_service.model.RetryUrlMessage;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class FrontierConsumer {

    private static final Logger logger = LoggerFactory.getLogger(FrontierConsumer.class);

    private final FrontierService frontierService;
    private final ExecutorService executorService;

    @Autowired
    public FrontierConsumer(FrontierService frontierService) {
        this.frontierService = frontierService;
        this.executorService = Executors.newFixedThreadPool(10); // Thread pool for parallel processing
    }

    /**
     * Consumer for "new_url" topic
     */
    @KafkaListener(
            topics = "new_url",
            containerFactory = "kafkaListenerContainerFactory",
            groupId = "frontier-service",
            concurrency = "5"
    )
    public void handleNewUrls(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        logger.debug("Received message from topic: {}, partition: {}, offset: {}", topic, partition, offset);

        try {

            processUrlAsync(message, "NEW");

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
            containerFactory = "retryKafkaListenerContainerFactory",
            groupId = "frontier-service",
            concurrency = "2"
    )
    public void handleRetryUrls(
            @Payload RetryUrlMessage message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        logger.debug("Received retry message from topic: {}, partition: {}, offset: {}", topic, partition, offset);

        try {

            if (message != null && message.getUrl() != null) {
                // Check retry conditions
                if (shouldRetry(message)) {
                    processUrlAsync(message.getUrl(), "RETRY");
                } else {
                    frontierService.addRetryUrl(message.getUrl());
                    logger.warn("Retry limit exceeded for URL: {}", message.getUrl());
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


    private boolean shouldRetry(RetryUrlMessage retryMessage) {
        // Check retry count limit
        if (retryMessage.getRetryCount() > 3) {
            return false;
        }

        // Check if enough time has passed since last attempt
        if (retryMessage.getLastAttempt() != null) {
            long timeSinceLastAttempt = Instant.now().toEpochMilli() - retryMessage.getLastAttempt().toEpochMilli();
            long minRetryDelay = 5 * 60 * 1000; // 5mins

            if (timeSinceLastAttempt < minRetryDelay) {
                logger.debug("Not enough time passed for retry: {}", retryMessage.getUrl());
                return false;
            }
        }

        return true;
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
}