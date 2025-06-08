package com.vdt.crawler.llm_parsing_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ParsingService {
    private final Logger logger = LoggerFactory.getLogger(ParsingService.class);

    private final ContentExtractor contentExtractor;
    private final SitemapExtractor sitemapExtractor;
    private final UrlExtractor urlExtractor;

    @Autowired
    public ParsingService(ContentExtractor contentExtractor, SitemapExtractor sitemapExtractor, UrlExtractor urlExtractor) {
        this.contentExtractor = contentExtractor;
        this.sitemapExtractor = sitemapExtractor;
        this.urlExtractor = urlExtractor;
    }


    public void parse(String rawHtml, int type) {
        if (type == Parsing.SITEMAP) {
            sitemapExtractor.parse(rawHtml);
        }
        contentExtractor.parse(rawHtml);
        urlExtractor.parse(rawHtml);
    }
}
