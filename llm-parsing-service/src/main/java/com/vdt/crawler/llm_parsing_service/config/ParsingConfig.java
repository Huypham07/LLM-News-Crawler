package com.vdt.crawler.llm_parsing_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ParsingConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
