package com.vdt.crawler.llm_parsing_service.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Domain {
    @Id
    private String id;

    @Indexed(unique = true)
    private String domain;

    @Field("seed_urls")
    private List<String> seedUrls;

    @Field("last_crawled")
    private Instant lastCrawled;

    @Field("create_at")
    private Instant createAt;

    private int priority;

    @Builder.Default
    private boolean active = true;
}
