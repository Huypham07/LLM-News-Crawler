package com.vdt.crawler.llm_parsing_service.service;

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
public class ParsingConsumer {
    private final Logger logger = LoggerFactory.getLogger(ParsingConsumer.class);

    private final ExecutorService executorService;
    private final ParsingService parsingService;

    @Autowired
    public ParsingConsumer(ParsingService parsingService) {
        this.parsingService = parsingService;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     *  Consumer for "parsing_tasks" topic
     */
    @KafkaListener(
            topics = "parsing_tasks",
            containerFactory = "parsingListenerContainerFactory",
            groupId = "parsing_group",
            concurrency = "5"
    )
    public void handleParsingTask(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        logger.debug("Received message from topic: {}, partition: {}, offset: {}", topic, partition, offset);

        try {

            processParsingAsync(message, Parsing.CONTENT);

            // Acknowledge message after success
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("Error Parsing new message at partition: {}, offset: {}", partition, offset, e);
        }

    }

    /**
     *  Consumer for "home_parsing_tasks" topic
     */
    @KafkaListener(
            topics = "home_parsing_tasks",
            containerFactory = "parsingListenerContainerFactory",
            groupId = "home_parsing_group",
            concurrency = "5"
    )
    public void handleHomeParsingTask(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        logger.debug("Received message from topic: {}, partition: {}, offset: {}", topic, partition, offset);

        try {

            processParsingAsync(message, Parsing.SITEMAP);

            // Acknowledge message after success
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("Error Parsing new message at partition: {}, offset: {}", partition, offset, e);
        }

    }

    private void processParsingAsync(String rawHtml, int type) {
        executorService.submit(() -> {
            try {
                logger.debug("Start parsing ...");
                parsingService.parse(rawHtml, type);
            } catch (Exception e) {
                logger.error("Error parsing: {}", e.getMessage());
            }
        });
    }
}
