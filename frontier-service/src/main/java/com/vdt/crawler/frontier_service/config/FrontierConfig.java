package com.vdt.crawler.frontier_service.config;

import com.vdt.crawler.frontier_service.service.robotstxt.PageFetcher;
import com.vdt.crawler.frontier_service.service.robotstxt.RobotstxtConfig;
import com.vdt.crawler.frontier_service.service.robotstxt.RobotstxtServer;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "frontier")
public class FrontierConfig {
    @Bean
    public PageFetcher pageFetcher() {
        return new PageFetcher(5000, 200, new SystemDefaultDnsResolver());
    }

    @Bean
    public RobotstxtConfig robotstxtConfig() {
        return new RobotstxtConfig();
    }

    @Bean
    public RobotstxtServer robotstxtServer(RobotstxtConfig config, PageFetcher fetcher) {
        return new RobotstxtServer(config, fetcher);
    }

    private int maxQueueSize = 10000;
    private int numberOfBackQueues = 10;
    private int maxRetryCount = 3;
    private long retryDelayMs = 60000; // 1 minute
    private int batchSize = 50;
    private int crawlerBatchSize = 10;

    // Thread pool configuration
    private int coreThreadPoolSize = 10;
    private int maxThreadPoolSize = 20;
    private int threadPoolQueueCapacity = 100;

    // Getters and setters
    public int getMaxQueueSize() { return maxQueueSize; }
    public void setMaxQueueSize(int maxQueueSize) { this.maxQueueSize = maxQueueSize; }

    public int getNumberOfBackQueues() { return numberOfBackQueues; }
    public void setNumberOfBackQueues(int numberOfBackQueues) { this.numberOfBackQueues = numberOfBackQueues; }

    public int getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }

    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getCrawlerBatchSize() { return crawlerBatchSize; }
    public void setCrawlerBatchSize(int crawlerBatchSize) { this.crawlerBatchSize = crawlerBatchSize; }

    public int getCoreThreadPoolSize() { return coreThreadPoolSize; }
    public void setCoreThreadPoolSize(int coreThreadPoolSize) { this.coreThreadPoolSize = coreThreadPoolSize; }

    public int getMaxThreadPoolSize() { return maxThreadPoolSize; }
    public void setMaxThreadPoolSize(int maxThreadPoolSize) { this.maxThreadPoolSize = maxThreadPoolSize; }

    public int getThreadPoolQueueCapacity() { return threadPoolQueueCapacity; }
    public void setThreadPoolQueueCapacity(int threadPoolQueueCapacity) { this.threadPoolQueueCapacity = threadPoolQueueCapacity; }
}