package com.vdt.crawler.content_store_service.model;

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

@Document(collection = "contents")
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
    @Field("content_title")
    private String contentTitle;

    @NotNull
    private String content;

    private String author;

    @Field("publish_at")
    private Instant lastAttempt;
}
