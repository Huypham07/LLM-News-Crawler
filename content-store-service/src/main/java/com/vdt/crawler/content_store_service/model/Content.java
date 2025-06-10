package com.vdt.crawler.content_store_service.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "contents")
@Setting(settingPath = "elasticsearch/content-settings.json")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Content {
    @Id
    private String id;

    @Field(type = FieldType.Keyword, name = "url")
    private String url;

    @NotNull
    @Field(type = FieldType.Text, name = "title", analyzer = "vi_analyzer")
    @JsonProperty("title")
    private String title;

    @NotNull
    @Field(type = FieldType.Text, name = "content", analyzer = "vi_analyzer")
    private String content;

    @Field(type = FieldType.Text, name = "author", analyzer = "vi_analyzer")
    private String author;

    @Field(type = FieldType.Date, name = "publish_at", format = DateFormat.date_time)
    @JsonProperty("publish_at")
    private Instant publishAt;

    @Field(type = FieldType.Dense_Vector, dims = 768, name = "content_embedding")
    @JsonProperty("content_embedding")
    private float[] contentEmbedding;

    @Override
    public String toString() {
        return "Content{" +
                "id='" + id + '\'' +
                ", url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", publishAt=" + publishAt +
                ", hasEmbedding=" + (contentEmbedding != null && contentEmbedding.length > 0) +
                '}';
    }
}