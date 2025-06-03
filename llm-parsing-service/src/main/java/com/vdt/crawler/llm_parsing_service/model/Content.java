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
    private String id;
    private String url;
    private String title;
    private String content;
    private String author;
    private Instant publishAt;
    private float[] contentEmbedding;
}
