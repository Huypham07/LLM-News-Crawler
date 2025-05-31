package com.vdt.crawler.llm_parsing_service.exception;

public class MaxTokenLLMException extends Exception {
    public MaxTokenLLMException(String message, String host) {
        super(message + " - host: " + host);
    }
}
