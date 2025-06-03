package com.vdt.crawler.fetcher_service.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FetcherMetrics {

    private final Counter fetchedUrlsTotal;
    private final Counter fetchedUrlsByDomain;
    private final Counter failedUrlsTotal;
    private final Counter failedUrlsByDomain;

    public FetcherMetrics(MeterRegistry meterRegistry) {
        this.fetchedUrlsTotal = Counter.builder("fetcher_fetched_urls_total")
                .description("Total number of URLs fetched successfully")
                .register(meterRegistry);

        this.fetchedUrlsByDomain = Counter.builder("fetcher_fetched_urls_by_domain_total")
                .description("Number of URLs fetched successfully by domain")
                .tag("domain", "unknown")
                .register(meterRegistry);

        this.failedUrlsTotal = Counter.builder("fetcher_failed_urls_total")
                .description("Total number of URLs failed to fetch (sent back to frontier for retry)")
                .register(meterRegistry);

        this.failedUrlsByDomain = Counter.builder("fetcher_failed_urls_by_domain_total")
                .description("Number of URLs failed to fetch by domain")
                .tag("domain", "unknown")
                .register(meterRegistry);
    }

    public void incrementFetchedUrls() {
        fetchedUrlsTotal.increment();
    }

    public void incrementFetchedUrls(String domain) {
        fetchedUrlsTotal.increment();
        Counter.builder("fetcher_fetched_urls_by_domain_total")
                .description("Number of URLs fetched successfully by domain")
                .tag("domain", domain)
                .register(Metrics.globalRegistry)
                .increment();
    }

    public void incrementFailedUrls() {
        failedUrlsTotal.increment();
    }

    public void incrementFailedUrls(String domain) {
        failedUrlsTotal.increment();
        Counter.builder("fetcher_failed_urls_by_domain_total")
                .description("Number of URLs failed to fetch by domain")
                .tag("domain", domain)
                .register(Metrics.globalRegistry)
                .increment();
    }
}