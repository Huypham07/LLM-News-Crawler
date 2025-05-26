package com.vdt.crawler.frontier_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FrontierServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrontierServiceApplication.class, args);
    }
}
