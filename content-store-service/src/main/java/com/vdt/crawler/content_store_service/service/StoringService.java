package com.vdt.crawler.content_store_service.service;

import com.vdt.crawler.content_store_service.model.Content;
import com.vdt.crawler.content_store_service.repository.ContentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class StoringService {

    private final Logger logger = LoggerFactory.getLogger(StoringService.class);
    private final ContentRepository contentRepository;

    @Autowired
    public StoringService(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    public void store(Content content) {
        try {
            if (content.getUrl() == null || content.getUrl().trim().isEmpty()) {
                logger.warn("Content URL is null or empty, skipping storage");
                return;
            }

            if (content.getTitle() == null || content.getTitle().trim().isEmpty()) {
                logger.warn("Content title is null or empty for URL: {}, skipping storage", content.getUrl());
                return;
            }

            if (content.getContent() == null || content.getContent().trim().isEmpty()) {
                logger.warn("Content body is null or empty for URL: {}, skipping storage", content.getUrl());
                return;
            }

            // Check if content already exists
            if (contentRepository.existsByUrl(content.getUrl())) {
                logger.info("Content with URL {} already exists, updating...", content.getUrl());
                updateExistingContent(content);
            } else {
                logger.info("Storing new content with URL: {}", content.getUrl());
                createNewContent(content);
            }

        }
        catch (NullPointerException e) {
            logger.warn("Content is null, skipping storage");
        }
        catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation while storing content with URL: {}", content.getUrl(), e);
        } catch (Exception e) {
            logger.error("Unexpected error while storing content with URL: {}", content.getUrl(), e);
            throw e;
        }
    }

    private void createNewContent(Content content) {
        try {
            // Save to Elasticsearch
            Content savedContent = contentRepository.save(content);
            logger.info("Successfully stored new content with ID: {} and URL: {}",
                    savedContent.getId(), savedContent.getUrl());

        } catch (Exception e) {
            logger.error("Failed to create new content with URL: {}", content.getUrl(), e);
            throw e;
        }
    }

    private void updateExistingContent(Content newContent) {
        try {
            Optional<Content> existingContentOpt = contentRepository.findByUrl(newContent.getUrl());

            if (existingContentOpt.isPresent()) {
                Content existingContent = existingContentOpt.get();

                // Update fields
                existingContent.setTitle(newContent.getTitle());
                existingContent.setContent(newContent.getContent());
                existingContent.setAuthor(newContent.getAuthor());
                existingContent.setPublishAt(newContent.getPublishAt());

                // Keep original created timestamp
                // existingContent.setCreatedAt() - don't update this

                Content savedContent = contentRepository.save(existingContent);
                logger.info("Successfully updated content with ID: {} and URL: {}",
                        savedContent.getId(), savedContent.getUrl());
            } else {
                logger.warn("Content with URL {} should exist but not found, creating new one", newContent.getUrl());
                createNewContent(newContent);
            }

        } catch (Exception e) {
            logger.error("Failed to update existing content with URL: {}", newContent.getUrl(), e);
            throw e;
        }
    }
}