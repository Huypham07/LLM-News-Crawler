package com.vdt.crawler.content_store_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
public class ContentStoreServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContentStoreServiceApplication.class, args);
	}

}
