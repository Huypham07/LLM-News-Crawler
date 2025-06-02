package com.vdt.crawler.llm_parsing_service.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "content_css_selector")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentCssSelector {
    @Id
    private String id;

    @NotNull
    @Indexed(unique = true)
    private String domain;

    @NotNull
    private String title;

    @NotNull
    private String content;

    private String author;

    @Field("publish_at")
    private String publishAt;
}
