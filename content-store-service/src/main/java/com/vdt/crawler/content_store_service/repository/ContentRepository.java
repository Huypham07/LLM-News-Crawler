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

    // Basic keyword search with fuzzy matching
    @Query("""
    {
      "bool": {
        "should": [
          {
            "match_phrase": {
              "title": {
                "query": "?0",
                "boost": 3.0
              }
            }
          },
          {
            "match_phrase": {
              "content": {
                "query": "?0",
                "boost": 1.0
              }
            }
          }
        ]
      }
    }
    """)
    Page<Content> findByTitleOrContentContaining(String keyword, Pageable pageable);

    boolean existsByUrl(String url);
}