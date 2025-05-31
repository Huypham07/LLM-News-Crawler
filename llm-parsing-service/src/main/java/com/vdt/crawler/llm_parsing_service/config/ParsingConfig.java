package com.vdt.crawler.llm_parsing_service.config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ParsingConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public Client genaiClient(@Value("${gemini.api-key}") String apiKey) {
        return Client.builder().apiKey(apiKey).build();
    }
}
