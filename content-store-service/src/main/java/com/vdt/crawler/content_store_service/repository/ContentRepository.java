package com.vdt.crawler.content_store_service.repository;

import com.vdt.crawler.content_store_service.model.Content;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContentRepository extends ElasticsearchRepository<Content, String> {

    Optional<Content> findByUrl(String url);

    // "fuzziness": "AUTO",
    @Query("""
        {
          "multi_match": {
            "query": "?0",
            "fields": ["title^2", "content^1"],
            "analyzer": "vi_analyzer",
            "type": "best_fields",
            "prefix_length": 1
          }
        }
        """)
    Page<Content> findByTitleOrContentContaining(String keyword, Pageable pageable);

    // Search by title only
    @Query("""
        {
          "match": {
            "title": {
              "query": "?0",
              "analyzer": "vi_analyzer",
              "boost": 2.0
            }
          }
        }
        """)
    Page<Content> findByTitle(String title, Pageable pageable);

// "fuzziness": "AUTO"
    @Query("""
        {
          "match": {
            "author": {
              "query": "?0",
              "analyzer": "vi_analyzer"
            }
          }
        }
        """)
    Page<Content> findByAuthorContaining(String author, Pageable pageable);


    @Query("""
        {
          "multi_match": {
            "query": "?0",
            "fields": ["title^3", "content^1", "author^2"],
            "analyzer": "vi_analyzer",
            "type": "most_fields",
            "minimum_should_match": "75%"
          }
        }
        """)
    Page<Content> searchAdvanced(String query, Pageable pageable);

    // Semantic search using vector similarity
    @Query("""
        {
          "script_score": {
            "query": {
              "exists": {
                "field": "content_embedding"
              }
            },
            "script": {
              "source": "cosineSimilarity(params.query_vector, 'content_embedding') + 1.0",
              "params": {
                "query_vector": ?0
              }
            }
          }
        }
        """)
    Page<Content> findBySemanticSimilarity(float[] queryVector, Pageable pageable);

    // Hybrid search: combine text search with semantic search
    @Query("""
        {
          "bool": {
            "should": [
              {
                "multi_match": {
                  "query": "?0",
                  "fields": ["title^3", "content^1"],
                  "analyzer": "vi_analyzer",
                  "type": "best_fields",
                  "boost": 1.0
                }
              },
              {
                "script_score": {
                  "query": {
                    "exists": {
                      "field": "content_embedding"
                    }
                  },
                  "script": {
                    "source": "cosineSimilarity(params.query_vector, 'content_embedding') + 1.0",
                    "params": {
                      "query_vector": ?1
                    }
                  },
                  "boost": 2.0
                }
              }
            ],
            "minimum_should_match": 1
          }
        }
        """)
    Page<Content> findByHybridSearch(String textQuery, float[] queryVector, Pageable pageable);

    // Get recent content
    Page<Content> findAllByOrderByPublishAtDesc(Pageable pageable);

    boolean existsByUrl(String url);
}