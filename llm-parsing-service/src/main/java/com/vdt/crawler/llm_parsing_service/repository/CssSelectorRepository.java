package com.vdt.crawler.llm_parsing_service.repository;

import com.vdt.crawler.llm_parsing_service.model.ContentCssSelector;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CssSelectorRepository extends MongoRepository<ContentCssSelector, String> {
    Optional<ContentCssSelector> findFirstByDomain(String domain);
}
