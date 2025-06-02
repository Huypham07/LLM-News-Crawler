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
            "fields": ["title^3", "content^1"],
            "analyzer": "vi_analyzer",
            "type": "best_fields",
            "prefix_length": 1
          }
        }
        """)
    Page<Content> findByTitleOrContentContaining(String keyword, Pageable pageable);

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


    // "fuzziness": "AUTO"
    @Query("""
        {
          "bool": {
            "must": [
              {
                "multi_match": {
                  "query": "?0",
                  "fields": ["title^3", "content^1"],
                  "analyzer": "vi_analyzer",
                  "type": "best_fields"
                }
              },
              {
                "match": {
                  "author": {
                    "query": "?1",
                    "analyzer": "vi_analyzer"
                  }
                }
              }
            ]
          }
        }
        """)
    Page<Content> findByKeywordAndAuthor(String keyword, String author, Pageable pageable);

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

    // Get recent content
    Page<Content> findAllByOrderByPublishAtDesc(Pageable pageable);

    boolean existsByUrl(String url);
}