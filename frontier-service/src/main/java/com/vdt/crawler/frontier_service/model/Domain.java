package com.vdt.crawler.frontier_service.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document(collection = "domains")
public class Domain {
    @Id
    private String id;

    @Indexed(unique = true)
    private String domain;

    @Field("seed_urls")
    private List<String> seedUrls;

    @Field("last_crawled")
    private Instant lastCrawled;

    @Field("last_response")
    private Instant lastResponse;

    @Field("create_at")
    private Instant createAt;

    private Integer priority;

    private Boolean active = true;

    public Domain() {}

    public Domain(String domain, List<String> seedUrls) {
        this.domain = domain;
        this.seedUrls = seedUrls;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public List<String> getSeedUrls() {
        return seedUrls;
    }

    public void setSeedUrls(List<String> seedUrls) {
        this.seedUrls = seedUrls;
    }

    public Instant getLastCrawled() {
        return lastCrawled;
    }

    public void setLastCrawled(Instant lastCrawled) {
        this.lastCrawled = lastCrawled;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Instant createAt) {
        this.createAt = createAt;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
