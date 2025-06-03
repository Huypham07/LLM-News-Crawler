package com.vdt.crawler.llm_parsing_service.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LLMParsingMetrics {

    private final Counter parsedArticlesTotal;
    private final Counter parsedArticlesByDomain;
    // failed
    private final Counter failedArticlesTotal;
    private final Counter failedArticlesByDomain;

    private final Counter newExtractedUrlsTotal;


    public LLMParsingMetrics(MeterRegistry meterRegistry) {
        this.parsedArticlesTotal = Counter.builder("llm_parsing_articles_parsed_total")
                .description("Total number of URLs parsed as articles successfully")
                .register(meterRegistry);

        this.parsedArticlesByDomain = Counter.builder("llm_parsing_articles_parsed_by_domain_total")
                .description("Number of URLs parsed as articles successfully by domain")
                .tag("domain", "unknown")
                .register(meterRegistry);

        this.failedArticlesTotal = Counter.builder("llm_parsing_articles_failed_total")
                .description("Total number of URLs parsed as articles failed")
                .register(meterRegistry);

        this.failedArticlesByDomain = Counter.builder("llm_parsing_articles_failed_by_domain_total")
                .description("Number of URLs parsed as articles failed by domain")
                .tag("domain", "unknown")
                .register(meterRegistry);

        this.newExtractedUrlsTotal = Counter.builder("llm_parsing_extracted_urls_total")
                .description("Total number of new URLs extracted and filtered to add back to frontier")
                .register(meterRegistry);
    }

    public void incrementParsedArticles() {
        parsedArticlesTotal.increment();
    }

    public void incrementParsedArticles(String domain) {
        parsedArticlesTotal.increment();
        Counter.builder("llm_parsing_articles_parsed_by_domain_total")
                .description("Number of URLs parsed as articles successfully by domain")
                .tag("domain", domain)
                .register(Metrics.globalRegistry)
                .increment();
    }

    public void incrementFailedArticles() {
        failedArticlesTotal.increment();
    }

    public void incrementFailedArticles(String domain) {
        failedArticlesTotal.increment();
        Counter.builder("llm_parsing_articles_failed_by_domain_total")
                .description("Number of URLs parsed as articles failed by domain")
                .tag("domain", domain)
                .register(Metrics.globalRegistry)
                .increment();
    }

    public void incrementNewExtractedUrls(int count) {
        newExtractedUrlsTotal.increment(count);
    }
}
