package com.vdt.crawler.fetcher_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryUrlMessage {
    private String url;
    private int retryCount;
    private Instant lastAttempt;
    private Integer httpStatus;
}