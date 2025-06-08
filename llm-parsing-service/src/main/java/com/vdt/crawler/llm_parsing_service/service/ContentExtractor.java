package com.vdt.crawler.llm_parsing_service.service;

import com.vdt.crawler.llm_parsing_service.exception.ContentParsingExcetion;
import com.vdt.crawler.llm_parsing_service.metric.LLMParsingMetrics;
import com.vdt.crawler.llm_parsing_service.model.Content;
import com.vdt.crawler.llm_parsing_service.util.UrlUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ContentExtractor implements Parsing{
    private final Logger logger = LoggerFactory.getLogger(ContentExtractor.class);
    private final KafkaTemplate<String, Content> contentKafkaTemplate;
    private final UrlFilter urlFilter;
    private final LLMParsing llmParsing;

    @Autowired
    public ContentExtractor(KafkaTemplate<String, Content> contentKafkaTemplate
            , UrlFilter urlFilter, LLMParsing llmParsing) {
        this.contentKafkaTemplate = contentKafkaTemplate;
        this.urlFilter = urlFilter;
        this.llmParsing = llmParsing;
    }

    @Override
    public void parse(String rawHtml) {
        Content result = getParsingResult(rawHtml);
        doAfterParse(result);
    }

    public void doAfterParse(Content result) {
        if (result == null) {
            return;
        }
        contentKafkaTemplate.send("storing_tasks", result);
        logger.info("Sent content: {} to Content Storage", result);
    }

    private Content getParsingResult(String rawHtml) {
        try {
            Content result = null;
            Document doc = Jsoup.parse(rawHtml);

            String url = UrlUtil.extractCurrentUrl(doc);

            if (url != null) {
                if (urlFilter.isLikelyArticleByUrl(url)) {
                    logger.info(">>> Likely article url, parse: {}", url);
                    result = llmParsing.getParsingContent(doc, url);
                } else {
                    logger.warn(">>> Not likely article url, extract urls only: {}", url);
                }
            }

            return result;
        }
        catch (ContentParsingExcetion e) {
            logger.error(e.getMessage());
            // TODO do something with url which parsing error by wrong config
            return null;
        }
        catch (Exception e) {
            logger.error("Error parsing HTML for URLs: {}", e.getMessage(), e);
            return null;
        }
    }
}
