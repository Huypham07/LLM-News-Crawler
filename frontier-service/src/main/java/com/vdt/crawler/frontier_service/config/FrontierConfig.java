package com.vdt.crawler.frontier_service.config;

import com.vdt.crawler.frontier_service.service.robotstxt.PageFetcher;
import com.vdt.crawler.frontier_service.service.robotstxt.RobotstxtConfig;
import com.vdt.crawler.frontier_service.service.robotstxt.RobotstxtServer;
import com.vdt.crawler.frontier_service.utils.DnsResolverWithCache;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FrontierConfig {
    @Bean
    public PageFetcher pageFetcher() {
        return new PageFetcher(5000, 200, new DnsResolverWithCache());
    }

    @Bean
    public RobotstxtConfig robotstxtConfig() {
        return new RobotstxtConfig();
    }

    @Bean
    public RobotstxtServer robotstxtServer(RobotstxtConfig config, PageFetcher fetcher) {
        return new RobotstxtServer(config, fetcher);
    }
}