package com.vdt.crawler.llm_parsing_service.exception;

public class RateLimitExceededException extends Exception {
    public RateLimitExceededException(String message) {
        super(message);
    }
}