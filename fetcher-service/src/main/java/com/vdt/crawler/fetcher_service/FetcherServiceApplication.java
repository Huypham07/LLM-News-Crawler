package com.vdt.crawler.fetcher_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class FetcherServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FetcherServiceApplication.class, args);
    }
}
