package com.vdt.crawler.content_store_service.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.vdt.crawler.content_store_service.model.Content;
import com.vdt.crawler.content_store_service.repository.ContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private final ContentRepository contentRepository;
    private final EmbeddingService embeddingService;
    private final ElasticsearchClient elasticsearchClient;

    @Autowired
    public SearchService(ContentRepository contentRepository, EmbeddingService embeddingService, ElasticsearchClient elasticsearchClient) {
        this.contentRepository = contentRepository;
        this.embeddingService = embeddingService;
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * Search content by keyword
     */
    public Page<Content> searchByKeyword(String keyword, int page, int size) {
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return Page.empty();
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

    private Page<Content> findBySemanticSimilarity(float[] queryVector, Pageable pageable) throws IOException {
        int from = (int) pageable.getOffset();
        int size = pageable.getPageSize();

        List<Float> vector = new ArrayList<>();
        for (float qv : queryVector) {
            vector.add(qv);
        }
        SearchResponse<Content> response = elasticsearchClient.search(s -> s
                        .index("contents")
                        .from(from)
                        .size(size)
                        .knn(knn -> knn
                                .field("content_embedding")
                                .queryVector(vector)
                                .k(Math.max(size * 2, 100))
                                .numCandidates(Math.max(size * 10, 1000))
                        ),
                Content.class
        );

        List<Content> results = response.hits().hits().stream()
                .map(Hit::source)
                .collect(Collectors.toList());

        long totalHits = response.hits().total() != null
                ? response.hits().total().value()
                : results.size();

        return new PageImpl<>(results, pageable, totalHits);
    }


    /**
     * Semantic search using embedding similarity
     */
    public Page<Content> searchBySemantic(String query, int page, int size) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return Page.empty();
            }

            // Generate embedding for the search query
            float[] queryEmbedding = embeddingService.generateEmbedding(query.trim());

            if (queryEmbedding.length == 0) {
                logger.warn("Failed to generate embedding for query: {}", query);
                return Page.empty();
            }

            Pageable pageable = PageRequest.of(page, size);
            logger.debug("{}", queryEmbedding);
            Page<Content> results = findBySemanticSimilarity(queryEmbedding, pageable);

            logger.info("Semantic search for query '{}' returned {} results", query, results.getTotalElements());
            return results;

        } catch (Exception e) {
            logger.error("Error in semantic search for query: {}", query, e);
            return Page.empty();
        }
    }
}