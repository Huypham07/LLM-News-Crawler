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
            @RequestParam(value = "size", defaultValue = "10") int size) {

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
            @RequestParam(value = "size", defaultValue = "10") int size) {

        logger.info("Semantic search request - query: '{}', page: {}, size: {}", query, page, size);

        Page<Content> results = searchService.searchBySemantic(query, page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/hybrid")
    @Operation(summary = "Hybrid search", description = "Combined traditional text search and semantic search for best results")
    public ResponseEntity<Page<Content>> hybridSearch(
            @Parameter(description = "Search query for hybrid search")
            @RequestParam(value = "q") String query,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "10") int size) {

        logger.info("Hybrid search request - query: '{}', page: {}, size: {}", query, page, size);

        Page<Content> results = searchService.hybridSearch(query, page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/author")
    @Operation(summary = "Search by author", description = "Search content by author name using Vietnamese analyzer")
    public ResponseEntity<Page<Content>> searchByAuthor(
            @Parameter(description = "Author name in Vietnamese")
            @RequestParam(value = "author") String author,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "10") int size) {

        logger.info("Author search request - author: '{}', page: {}, size: {}", author, page, size);

        Page<Content> results = searchService.searchByAuthor(author, page, size);
        return ResponseEntity.ok(results);
    }


    @GetMapping("/advanced")
    @Operation(summary = "Advanced search", description = "Advanced multi-field search with Vietnamese analyzer")
    public ResponseEntity<Page<Content>> advancedSearch(
            @Parameter(description = "Search query (searches across title, content, and author)")
            @RequestParam(value = "q") String query,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "10") int size) {

        logger.info("Advanced search request - query: '{}', page: {}, size: {}", query, page, size);

        Page<Content> results = searchService.advancedSearch(query, page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/title")
    @Operation(summary = "Search by title", description = "Search content by title only")
    public ResponseEntity<Page<Content>> searchByTitle(
            @Parameter(description = "Title to search for")
            @RequestParam(value = "title") String title,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "10") int size) {

        logger.info("Title search request - title: '{}', page: {}, size: {}", title, page, size);

        Page<Content> results = searchService.searchByTitle(title, page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recent content", description = "Get most recently added content")
    public ResponseEntity<Page<Content>> getRecentContent(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(value = "size", defaultValue = "10") int size) {

        logger.info("Recent content request - page: {}, size: {}", page, size);

        Page<Content> results = searchService.getRecentContent(page, size);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/exists")
    @Operation(summary = "Check if content exists", description = "Check if content exists by URL")
    public ResponseEntity<Boolean> existsByUrl(
            @Parameter(description = "Content URL")
            @RequestParam(value = "url") String url) {

        logger.info("Existence check request - url: '{}'", url);

        boolean exists = searchService.existsByUrl(url);
        return ResponseEntity.ok(exists);
    }
}