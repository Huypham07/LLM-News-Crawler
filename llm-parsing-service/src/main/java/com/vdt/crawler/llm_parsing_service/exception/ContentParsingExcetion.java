package com.vdt.crawler.llm_parsing_service.exception;

import com.vdt.crawler.llm_parsing_service.model.Content;

public class ContentParsingExcetion extends Exception {
    Content content;

    public ContentParsingExcetion(Content content, String url) {
        super("error with parsing content of url: " + url);
        this.content = content;
    }

    public Content getContent() {
        return content;
    }
}