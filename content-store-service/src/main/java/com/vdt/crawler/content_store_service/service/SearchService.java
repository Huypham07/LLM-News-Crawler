package com.vdt.crawler.content_store_service.service;

import com.vdt.crawler.content_store_service.model.Content;
import com.vdt.crawler.content_store_service.repository.ContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private final ContentRepository contentRepository;
    private final EmbeddingService embeddingService;

    @Autowired
    public SearchService(ContentRepository contentRepository, EmbeddingService embeddingService) {
        this.contentRepository = contentRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * Search content by keyword
     */
    public Page<Content> searchByKeyword(String keyword, int page, int size) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return getRecentContent(page, size);
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("publish_at").descending());
            Page<Content> results = contentRepository.findByTitleOrContentContaining(keyword.trim(), pageable);

            logger.info("Search for keyword '{}' returned {} results", keyword, results.getTotalElements());
            return results;

        } catch (Exception e) {
            logger.error("Error searching by keyword: {}", keyword, e);
            return Page.empty();
        }
    }

    /**
     * Search by author using Vietnamese analyzer
     */
    public Page<Content> searchByAuthor(String author, int page, int size) {
        try {
            if (author == null || author.trim().isEmpty()) {
                return Page.empty();
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("publish_at").descending());
            Page<Content> results = contentRepository.findByAuthorContaining(author.trim(), pageable);

            logger.info("Search for author '{}' returned {} results", author, results.getTotalElements());
            return results;

        } catch (Exception e) {
            logger.error("Error searching by author: {}", author, e);
            return Page.empty();
        }
    }


    /**
     * Advanced search with multiple fields
     */
    public Page<Content> advancedSearch(String query, int page, int size) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return getRecentContent(page, size);
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("publish_at").descending());
            Page<Content> results = contentRepository.searchAdvanced(query.trim(), pageable);

            logger.info("Advanced search for query '{}' returned {} results", query, results.getTotalElements());
            return results;

        } catch (Exception e) {
            logger.error("Error in advanced search - query: {}", query, e);
            return Page.empty();
        }
    }

    /**
     * Search by title only
     */
    public Page<Content> searchByTitle(String title, int page, int size) {
        try {
            if (title == null || title.trim().isEmpty()) {
                return Page.empty();
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("publish_at").descending());
            Page<Content> results = contentRepository.findByTitle(title.trim(), pageable);

            logger.info("Title search for '{}' returned {} results", title, results.getTotalElements());
            return results;

        } catch (Exception e) {
            logger.error("Error searching by title: {}", title, e);
            return Page.empty();
        }
    }

    /**
     * Get recent content
     */
    public Page<Content> getRecentContent(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("created_at").descending());
            Page<Content> results = contentRepository.findAllByOrderByPublishAtDesc(pageable);

            logger.info("Retrieved {} recent content items", results.getTotalElements());
            return results;

        } catch (Exception e) {
            logger.error("Error getting recent content", e);
            return Page.empty();
        }
    }

    /**
     * Check if content exists by URL
     */
    public boolean existsByUrl(String url) {
        try {
            if (url == null || url.trim().isEmpty()) {
                return false;
            }

            return contentRepository.existsByUrl(url.trim());

        } catch (Exception e) {
            logger.error("Error checking existence by URL: {}", url, e);
            return false;
        }
    }

    /**
     * Semantic search using embedding similarity
     */
    public Page<Content> searchBySemantic(String query, int page, int size) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return getRecentContent(page, size);
            }

            // Generate embedding for the search query
            float[] queryEmbedding = embeddingService.generateEmbedding(query.trim());

            if (queryEmbedding.length == 0) {
                logger.warn("Failed to generate embedding for query: {}", query);
                return Page.empty();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<Content> results = contentRepository.findBySemanticSimilarity(queryEmbedding, pageable);

            logger.info("Semantic search for query '{}' returned {} results", query, results.getTotalElements());
            return results;

        } catch (Exception e) {
            logger.error("Error in semantic search for query: {}", query, e);
            return Page.empty();
        }
    }

    /**
     * Hybrid search combining keyword and semantic similarity
     */
    public Page<Content> hybridSearch(String query, int page, int size) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return getRecentContent(page, size);
            }

            // Generate embedding for the search query
            float[] queryEmbedding = embeddingService.generateEmbedding(query.trim());

            if (queryEmbedding.length == 0) {
                logger.warn("Failed to generate embedding for hybrid search, falling back to text search");
                return searchByKeyword(query, page, size);
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<Content> results = contentRepository.findByHybridSearch(query.trim(), queryEmbedding, pageable);

            logger.info("Hybrid search for query '{}' returned {} results", query, results.getTotalElements());
            return results;

        } catch (Exception e) {
            logger.error("Error in hybrid search for query: {}", query, e);
            return Page.empty();
        }
    }
}