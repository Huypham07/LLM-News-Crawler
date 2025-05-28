package com.vdt.crawler.fetcher_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "url_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class URLMetaData {
    @Id
    private String id;

    @Indexed(unique = true)
    private String url;

    @Field("raw_html")
    private String rawHtml;

    @Field("status_code")
    private Integer statusCode;

    @Field("retry_count")
    private int retryCount;

    @Field("last_attempt")
    private Instant lastAttempt;
}
