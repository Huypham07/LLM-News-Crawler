package com.vdt.crawler.frontier_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FrontierScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FrontierScheduler.class);

    private final FrontierService frontierService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @Autowired
    public FrontierScheduler(FrontierService frontierService, KafkaTemplate<String, String> kafkaTemplate) {
        this.frontierService = frontierService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Scheduled task to move URLs from front queue to back queue
     * Runs every 3 seconds
     */
    @Scheduled(fixedDelay = 3000)
    public void processQueue() {
        if (isProcessing.compareAndSet(false, true)) {
            try {
                processUrlsFromFrontToBack();
            } catch (Exception e) {
                logger.error("Error in scheduled queue processing", e);
            } finally {
                isProcessing.set(false);
            }
        }
    }

    /**
     * Scheduled task to send URLs to crawlers
     * Runs every 2 seconds
     */
    @Scheduled(fixedDelay = 2000)
    public void sendUrlsToFetcher() {
        try {
            List<String> urlsToSend = new ArrayList<>();

            // Get URLs from back queue (respecting politeness)
            for (int i = 0; i < 10; i++) { // Max 10 URLs per batch
                String url = frontierService.getNextUrlFromBackQueue();
                if (url != null) {
                    urlsToSend.add(url);
                } else {
                    break;
                }
            }

            if (!urlsToSend.isEmpty()) {
                // Send to crawler service via Kafka
                for (String url : urlsToSend) {
                    kafkaTemplate.send("fetching_tasks", url);
                }

                logger.debug("Sent {} URLs to fetcher", urlsToSend.size());
            }

        } catch (Exception e) {
            logger.error("Error sending URLs to fetcher", e);
        }
    }

    /**
     * Scheduled task to move URLs from retry set to front queue
     * Runs every 5mins
     */
    @Scheduled(fixedDelay = 300000)
    public void retryUrlScheduler() {
        try {
            List<String> retryUrls = new ArrayList<>();

            // Get URLs from back queue (respecting politeness)
            for (int i = 0; i < 5; i++) { // Max 10 URLs per batch
                String url = frontierService.getNextUrlfromRetrySet();
                if (url != null) {
                    retryUrls.add(url);
                } else {
                    break;
                }
            }

            if (!retryUrls.isEmpty()) {
                frontierService.addToFrontier(retryUrls);
            }

        } catch (Exception e) {
            logger.error("Error retry URLs", e);
        }
    }

    private void processUrlsFromFrontToBack() {
        // Process URLs from front queue to back queue
        List<String> processedUrls = new ArrayList<>();

        int batchSize = 50;

        // Get URLs from front queue
        for (int i = 0; i < batchSize; i++) { // Process max 50 URLs per cycle
            String url = frontierService.getNextUrlFromFrontQueue();
            if (url != null) {
                frontierService.moveToBackQueue(url);
                processedUrls.add(url);
            } else {
                break;
            }
        }

        if (!processedUrls.isEmpty()) {
            logger.debug("Moved {} URLs from front to back queue", processedUrls.size());
        }
    }



    /**
     * Log frontier statistics periodically
     * Run every 30 seconds
     */
    @Scheduled(fixedDelay = 30000)
    public void logStatistics() {
        try {
            var stats = frontierService.getFrontierStats();
            logger.info("Frontier Stats - Front: {}, Back: {}, Domains: {}",
                    stats.get("totalFrontUrls"),
                    stats.get("totalBackUrls"),
                    stats.get("totalDomainMappings"));
        } catch (Exception e) {
            logger.warn("Error logging statistics", e);
        }
    }
}