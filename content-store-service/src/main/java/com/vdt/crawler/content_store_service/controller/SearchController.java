package com.vdt.crawler.content_store_service.controller;

import com.vdt.crawler.content_store_service.model.Content;
import com.vdt.crawler.content_store_service.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Vietnamese Content Search API")
public class SearchController {

    private final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "Search content by keyword", description = "Search for content using Vietnamese analyzer")
    public ResponseEntity<Page<Content>> searchByKeyword(
            @Parameter(description = "Search keyword in Vietnamese")
            @RequestParam(value = "q", required = false) String keyword,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "5") int size) {

        logger.info("Search request - keyword: '{}', page: {}, size: {}", keyword, page, size);

        Page<Content> results = searchService.searchByKeyword(keyword, page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/semantic")
    @Operation(summary = "Semantic search", description = "AI-powered semantic search using Gemini embeddings")
    public ResponseEntity<Page<Content>> semanticSearch(
            @Parameter(description = "Search query for semantic similarity")
            @RequestParam(value = "q") String query,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "5") int size) {

        logger.info("Semantic search request - query: '{}', page: {}, size: {}", query, page, size);

        Page<Content> results = searchService.searchBySemantic(query, page, size);
        return ResponseEntity.ok(results);
    }
}