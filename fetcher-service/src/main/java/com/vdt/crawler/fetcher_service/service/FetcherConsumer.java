package com.vdt.crawler.fetcher_service.service;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FetcherConsumer {
    private static final Logger logger = LoggerFactory.getLogger(FetcherConsumer.class);

    private final FetcherService fetcherService;
    private final ExecutorService executorService;

    @Autowired
    public FetcherConsumer(FetcherService fetcherService) {
        this.fetcherService = fetcherService;
        this.executorService = Executors.newFixedThreadPool(10); // Thread pool for parallel processing
    }

    /**
     *  Consumer for "fetching_tasks" topic
     */
    @KafkaListener(
            topics = "fetching_tasks",
            groupId = "fetching_group",
            concurrency = "5"
    )
    public void handleCrawlerTask(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        logger.debug("Received message from topic: {} partition: {} offset: {}", topic, partition, offset);

        try {
            processUrlAsync(message.trim());

            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("Error fetching url: {}", message, e);
        }
    }

    private void processUrlAsync(String url) {
        executorService.submit(() -> {
            try {
                logger.debug("Fetching URL: {}", url);
                fetcherService.processUrl(url);
            } catch (Exception e) {
                logger.error("Error fetching URL {}: {}", url, e.getMessage());
                e.printStackTrace();
            }
        });
    }
}


