package com.vdt.crawler.frontier_service.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FrontierMetrics {
    private final Counter scheduledUrlsTotal;
    private final Counter processedUrlsTotal;
    private final Counter rejectedUrlsTotal;
    private final Counter processedUrlsByDomain;
    private final Counter rejectedUrlsByDomain;

    public FrontierMetrics(MeterRegistry meterRegistry) {
        this.scheduledUrlsTotal = Counter.builder("frontier_scheduled_urls_total")
                .description("Total number of URLs scheduled to crawl")
                .register(meterRegistry);

        this.processedUrlsTotal = Counter.builder("frontier_processed_urls_total")
                .description("Total number of URLs processed to next step")
                .register(meterRegistry);

        this.rejectedUrlsTotal = Counter.builder("frontier_rejected_urls_total")
                .description("Total number of URLs rejected by robots.txt or not in domain list")
                .register(meterRegistry);

        this.processedUrlsByDomain = Counter.builder("frontier_processed_urls_by_domain_total")
                .description("Number of URLs processed by domain")
                .tag("domain", "unknown")
                .register(meterRegistry);

        this.rejectedUrlsByDomain = Counter.builder("frontier_rejected_urls_by_domain_total")
                .description("Number of URLs rejected by robots.txt or not in domain list by domain")
                .tag("domain", "unknown")
                .register(meterRegistry);
    }

    public void incrementScheduledUrlsTotal() {
        scheduledUrlsTotal.increment();
    }

    public void incrementProcessedUrls() {
        processedUrlsTotal.increment();
    }

    public void incrementProcessedUrls(String domain) {
        processedUrlsTotal.increment();
        Counter.builder("frontier_processed_urls_by_domain_total")
                .description("Number of URLs processed by domain")
                .tag("domain", domain)
                .register(Metrics.globalRegistry)
                .increment();
    }

    public void incrementRejectedUrls() {
        rejectedUrlsTotal.increment();
    }

    public void incrementRejectedUrls(String domain) {
        rejectedUrlsTotal.increment();
        Counter.builder("frontier_rejected_urls_by_domain_total")
                .description("Number of URLs rejected by robots.txt or not in domain list by domain")
                .tag("domain", domain)
                .register(Metrics.globalRegistry)
                .increment();
    }
}
