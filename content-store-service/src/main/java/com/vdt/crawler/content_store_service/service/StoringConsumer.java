package com.vdt.crawler.content_store_service.service;

import com.vdt.crawler.content_store_service.model.Content;
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
public class StoringConsumer {
    private final Logger logger = LoggerFactory.getLogger(StoringConsumer.class);

    private final ExecutorService executorService;
    private final StoringService storingService;

    @Autowired
    public StoringConsumer(StoringService storingService) {
        this.storingService = storingService;
        this.executorService = Executors.newFixedThreadPool(8);
    }

    /**
     *  Consumer for "storing_tasks" topic
     */
    @KafkaListener(
            topics = "storing_tasks",
            groupId = "storing_group",
            concurrency = "8",
            containerFactory = "parsingListenerContainerFactory"
    )
    public void handleStoringTask(
            @Payload Content message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        logger.debug("Received message from topic: {}, partition: {}, offset: {}", topic, partition, offset);

        try {
            processContentAsync(message);

            // Acknowledge message after success
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("Error Parsing new message at partition: {}, offset: {}", partition, offset, e);
        }

    }

    private void processContentAsync(Content content) {
        executorService.submit(() -> {
            try {
                logger.debug("Start storing: {}", content);
                storingService.store(content);
            } catch (Exception e) {
                logger.error("Error parsing: {}", e.getMessage());
            }
        });
    }
}
