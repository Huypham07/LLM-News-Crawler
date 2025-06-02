package com.vdt.crawler.fetcher_service.config;

import com.vdt.crawler.fetcher_service.service.PageFetcher;
import com.vdt.crawler.fetcher_service.util.DnsResolverWithCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class FetcherConfig {
    @Bean
    public PageFetcher pageFetcher() {
        return new PageFetcher(5000, 200, new DnsResolverWithCache());
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
