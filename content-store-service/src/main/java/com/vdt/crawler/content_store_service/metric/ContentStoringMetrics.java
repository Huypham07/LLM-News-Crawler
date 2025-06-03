package com.vdt.crawler.content_store_service.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ContentStoringMetrics {

    private final Counter storedContentTotal;
    private final Counter failedContentTotal;

    public ContentStoringMetrics(MeterRegistry meterRegistry) {
        this.storedContentTotal = Counter.builder("content_storing_stored_content_total")
                .description("Total number of content items stored")
                .register(meterRegistry);

        this.failedContentTotal = Counter.builder("content_storing_failed_content_total")
                .description("Total number of content items stored failed")
                .register(meterRegistry);
    }

    public void incrementStoredContent() {
        storedContentTotal.increment();
    }

    public void incrementFailedContent() {
        failedContentTotal.increment();
    }
}