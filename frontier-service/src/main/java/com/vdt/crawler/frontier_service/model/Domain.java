package com.vdt.crawler.frontier_service.model;


import jakarta.validation.constraints.NotNull;
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

@Document(collection = "domains")
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

    private Boolean active = true;
}
