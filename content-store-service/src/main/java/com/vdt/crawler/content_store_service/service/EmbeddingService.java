package com.vdt.crawler.content_store_service.service;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmbeddingService {
    private final Client genaiClient;
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);


    @Autowired
    public EmbeddingService(Client genaiClient) {
        this.genaiClient = genaiClient;
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

            EmbedContentResponse response = genaiClient.models
                    .embedContent("text-embedding-004", text,
                            EmbedContentConfig.builder()
                                    .taskType("SEMANTIC_SIMILARITY")
                                    .build()
                    );

            if (response != null && response.embeddings().isPresent()) {
                List<Float> embeddingsOpt = response.embeddings().get().get(0).values().orElse(null);

                if (embeddingsOpt != null) {
                    float[] embeddings = new float[embeddingsOpt.size()];
                    for (int i = 0; i < embeddings.length; i++) {
                        embeddings[i] = embeddingsOpt.get(i);
                    }

                    return embeddings;
                }
            }

            return new float[0];
        } catch (Exception e) {
            logger.error("Error generating embedding for text: {}", text.substring(0, Math.min(100, text.length())), e);
            return new float[0];
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