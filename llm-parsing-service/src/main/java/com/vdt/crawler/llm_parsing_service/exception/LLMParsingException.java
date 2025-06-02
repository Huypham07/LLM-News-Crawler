package com.vdt.crawler.llm_parsing_service.exception;

public class LLMParsingException extends Exception{
    public LLMParsingException(String message, String host) {
        super(message + " - host: " + host);
    }
}
