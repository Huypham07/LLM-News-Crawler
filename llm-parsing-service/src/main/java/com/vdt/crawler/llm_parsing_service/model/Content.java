package com.vdt.crawler.llm_parsing_service.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Content {
    @Id
    private String id;

    @Indexed(unique = true)
    private String url;

    @NotNull
    private String title;

    @NotNull
    private String content;

    private String author;

    @Field("publish_at")
    private Instant publishAt;
}
