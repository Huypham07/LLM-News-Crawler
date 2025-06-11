package com.vdt.crawler.content_store_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);


    @Autowired
    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Generate embedding vector for given text using Gemini text-embedding-004
     */
    public float[] generateEmbedding(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                logger.warn("Empty text provided for embedding generation");
                return new float[0];
            }

            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));


            if (response != null && !response.getResults().isEmpty()) {
                float[] embeddings = response.getResults().get(0).getOutput();

                logger.debug("Generated embedding with dimension: {}", embeddings.length);
                return embeddings;
            }

            return null;
        } catch (Exception e) {
            logger.error("Error generating embedding for text: {}", text.substring(0, Math.min(100, text.length())), e);
            return null;
        }
    }


    /**
     * Generate embedding for title + content combination
     */
    public float[] generateContentEmbedding(String title, String content) {
        String combinedText = combineTextForEmbedding(title, content);
        return generateEmbedding(combinedText);
    }

    /**
     * Combine title and content for embedding generation
     */
    private String combineTextForEmbedding(String title, String content) {
        StringBuilder combined = new StringBuilder();

        if (title != null && !title.trim().isEmpty()) {
            combined.append(title.trim());
        }

        if (content != null && !content.trim().isEmpty()) {
            if (!combined.isEmpty()) {
                combined.append(" ");
            }
            // Limit content length. 2,048 Input token limit

            String trimmedContent = content.trim();
            if (trimmedContent.length() > 8000) {
                trimmedContent = trimmedContent.substring(0, 8000);
            }
            combined.append(trimmedContent);
        }

        return combined.toString();
    }
}