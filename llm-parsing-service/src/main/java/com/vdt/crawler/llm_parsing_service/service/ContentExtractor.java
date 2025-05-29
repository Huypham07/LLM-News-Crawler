package com.vdt.crawler.llm_parsing_service.service;

import com.vdt.crawler.llm_parsing_service.model.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContentExtractor implements Parsing{
    private final Logger logger = LoggerFactory.getLogger(ContentExtractor.class);
    private final KafkaTemplate<String, Content> contentKafkaTemplate;

    @Autowired
    public ContentExtractor(KafkaTemplate<String, Content> contentKafkaTemplate) {
        this.contentKafkaTemplate = contentKafkaTemplate;
    }

    @Override
    public void parse(String rawHtml) {
        Content result = getParsingResult(rawHtml);
        doAfterParse(result);
    }

    public void doAfterParse(Content result) {
        if (result == null) {
            logger.info("Not an article url, skip scraping content");
            return;
        }
        contentKafkaTemplate.send("", result);
        logger.debug("Sent content: {} to Content Storage", result);
    }

    private Content getParsingResult(String rawHtml) {
        Content result = null;

        return result;
    }
}
