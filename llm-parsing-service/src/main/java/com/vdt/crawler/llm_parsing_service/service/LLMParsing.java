package com.vdt.crawler.llm_parsing_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.vdt.crawler.llm_parsing_service.exception.ContentParsingExcetion;
import com.vdt.crawler.llm_parsing_service.exception.LLMParsingException;
import com.vdt.crawler.llm_parsing_service.exception.MaxTokenLLMException;
import com.vdt.crawler.llm_parsing_service.exception.RateLimitExceededException;
import com.vdt.crawler.llm_parsing_service.metric.LLMParsingMetrics;
import com.vdt.crawler.llm_parsing_service.model.Content;
import com.vdt.crawler.llm_parsing_service.model.ContentCssSelector;
import com.vdt.crawler.llm_parsing_service.repository.CssSelectorRepository;
import com.vdt.crawler.llm_parsing_service.util.DateTimeUtil;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LLMParsing {
    private final Logger logger = LoggerFactory.getLogger(LLMParsing.class);
    private final Map<String, ContentCssSelector> contentCssSelectorMap;
    private final CssSelectorRepository cssSelectorRepository;
    private final Client genaiClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final int MAX_CONCURRENT_REQUESTS = 8; // Slightly lower than API limit
    private static final int MAX_TOKENS_PER_REQUEST = 125000; // Gemini 2.0 Flash limit
    private static final int CHARS_PER_TOKEN = 4;

    // Rate limiting - 30 requests per minute
    private final Semaphore concurrentLimiter = new Semaphore(MAX_CONCURRENT_REQUESTS);
    private final Queue<Long> requestTimestamps = new ConcurrentLinkedQueue<>();
    private final ReentrantLock rateLimitLock = new ReentrantLock();

    private final LLMParsingMetrics llmParsingMetrics;

    @Autowired
    public LLMParsing(CssSelectorRepository cssSelectorRepository, Client genaiClient, LLMParsingMetrics llmParsingMetrics) {
        this.cssSelectorRepository = cssSelectorRepository;
        contentCssSelectorMap = new ConcurrentHashMap<>();
        this.genaiClient = genaiClient;
        this.objectMapper = new ObjectMapper();
        this.llmParsingMetrics = llmParsingMetrics;
    }


    public Content getParsingContent(Document doc, String url) throws ContentParsingExcetion {
        try {
            Content result = new Content();
            result.setUrl(url);
            URL urlObj = new URL(url);

            String host = urlObj.getHost();

            ContentCssSelector cssSelector = contentCssSelectorMap.computeIfAbsent(host, k -> cssSelectorRepository.findFirstByDomain(k)
                    .orElseGet(() -> {
                        ContentCssSelector newSelector = getCssSelectorWithRetry(doc, host);
                        return cssSelectorRepository.save(newSelector);
                    }));

            // Parse content using CSS selectors
            if (cssSelector.getTitle() != null && !cssSelector.getTitle().isEmpty()) {
                String title = parseElementText(doc, cssSelector.getTitle());
                result.setTitle(title);
            }

            if (cssSelector.getContent() != null && !cssSelector.getContent().isEmpty()) {
                String content = parseElementText(doc, cssSelector.getContent());
                result.setContent(content);
            }


            if (cssSelector.getAuthor() != null && !cssSelector.getAuthor().isEmpty()) {
                String author = parseElementText(doc, cssSelector.getAuthor());
                result.setAuthor(author);
            }

            if (cssSelector.getPublishAt() != null && !cssSelector.getPublishAt().isEmpty()) {
                String dateStr = parseElementText(doc, cssSelector.getPublishAt());
                Instant publishAt = DateTimeUtil.parseDate(dateStr);
                result.setPublishAt(publishAt);
            }

            if (result.getTitle() == null || result.getTitle().isEmpty() ||
                    result.getContent() == null || result.getContent().isEmpty()) {
                llmParsingMetrics.incrementFailedArticles(host);
                throw new ContentParsingExcetion(result, url);
            }
            logger.debug(">>> result: {}", result);
            llmParsingMetrics.incrementParsedArticles(host);
            return result;
        } catch (MalformedURLException e) {
            logger.error("Invalid URL: {}", url, e);
            return null;
        }
    }

    /**
     * Improved rate limiting with exponential backoff retry
     */
    private ContentCssSelector getCssSelectorWithRetry(Document doc, String host) {
        int maxRetries = 3;
        long baseDelayMs = 1000; // 1 second base delay

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return getCssSelector(doc, host);
            } catch (RateLimitExceededException e) {
                if (attempt == maxRetries - 1) {
                    logger.error("Max retries exceeded for domain: {}", host);
                    return createDefaultCssSelector(host);
                }

                long delayMs = baseDelayMs * (1L << attempt); // Exponential backoff
                logger.warn("Rate limit hit for domain: {}, retrying in {}ms (attempt {}/{})",
                        host, delayMs, attempt + 1, maxRetries);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return createDefaultCssSelector(host);
                }
            } catch (Exception e) {
                logger.error("Failed to get CSS selector for domain: {}", host, e);
                return createDefaultCssSelector(host);
            }
        }

        return createDefaultCssSelector(host);
    }

    /**
     * Unified rate limiting mechanism
     */
    private ContentCssSelector getCssSelector(Document doc, String host) throws LLMParsingException, RateLimitExceededException, MaxTokenLLMException {
        // Track request timing
        long startTime = System.currentTimeMillis();

        // Check rate limit and wait if needed
        waitForRateLimit();

        boolean acquired = false;
        try {
            acquired = concurrentLimiter.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RateLimitExceededException("Could not acquire concurrent permit within 30 seconds");
            }

            // Clean and optimize HTML
            String optimizedHtml = optimizeHtmlForLLM(doc);

            // Check token limit
            if (estimateTokens(optimizedHtml) > MAX_TOKENS_PER_REQUEST) {
                throw new MaxTokenLLMException("HTML too large", host);
            }

            recordRequest(startTime);
            return makeGeminiApiCall(optimizedHtml, host, startTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMParsingException("Thread interrupted while waiting for rate limit", host);
        } finally {
            if (acquired) {
                concurrentLimiter.release();
            }
        }
    }

    /**
     * Non-blocking rate limit check with intelligent waiting
     */
    private void waitForRateLimit() throws RateLimitExceededException {
        rateLimitLock.lock();
        try {
            long currentTime = System.currentTimeMillis();

            // Clean up old requests (older than 1 minute)
            requestTimestamps.removeIf(timestamp -> currentTime - timestamp > 60_000);

            // Check if we need to wait
            if (requestTimestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
                Long oldestRequest = requestTimestamps.peek();
                if (oldestRequest != null) {
                    long waitTime = 60_000 - (currentTime - oldestRequest);
                    if (waitTime > 0) {
                        logger.info("Rate limit reached, need to wait {}ms", waitTime);

                        // Instead of blocking, throw exception with wait time info
                        throw new RateLimitExceededException(
                                String.format("Rate limit exceeded. Need to wait %d ms", waitTime));
                    }
                }
            }
        } finally {
            rateLimitLock.unlock();
        }
    }

    /**
     * Record request timestamp for rate limiting
     */
    private void recordRequest(long timestamp) {
        rateLimitLock.lock();
        try {
            requestTimestamps.offer(timestamp);

            // Keep queue size reasonable
            if (requestTimestamps.size() > MAX_REQUESTS_PER_MINUTE * 2) {
                requestTimestamps.poll();
            }
        } finally {
            rateLimitLock.unlock();
        }
    }

    /**
     * Make API call to Gemini with proper error handling
     */
    private ContentCssSelector makeGeminiApiCall(String optimizedHtml, String host, long startTime)
            throws LLMParsingException {
        try {
            String prompt = buildOptimizedPrompt(optimizedHtml);

            logger.info("Sending request to Gemini for domain: {}, estimated tokens: {}",
                    host, estimateTokens(prompt));

            GenerateContentResponse response = genaiClient.models.generateContent(
                    "gemini-2.0-flash-lite-001", prompt, null);

            long endTime = System.currentTimeMillis();
            logger.info("Gemini API call completed for domain: {} in {}ms", host, endTime - startTime);

            String responseText = response.text();
            if (responseText == null || responseText.trim().isEmpty()) {
                logger.error("Empty response from Gemini for domain: {}", host);
                throw new LLMParsingException("Empty response from Gemini API", host);
            }

            logger.debug(responseText);
            return parseCssSelectorResponse(responseText, host);

        } catch (Exception e) {
            logger.error("Error calling Gemini API for domain: {}", host, e);
            throw new LLMParsingException("Failed to call Gemini API", host);
        }
    }

    private String optimizeHtmlForLLM(Document doc) {
        // Clone document to avoid modifying original
        Document cleanDoc = doc.clone();

        // Remove unnecessary elements
//        cleanDoc.select("script, style, noscript, iframe, embed, object").remove();
//        cleanDoc.select("head, header, nav, footer, .advertisement, .ads").remove();
//        cleanDoc.select("[class*='ad'], [id*='ad'], [class*='banner'], [id*='banner']").remove();
//        cleanDoc.select(".social, .share, .comment, .related").remove();

        cleanDoc.select("script, style").remove();
        cleanDoc.select("head").remove();
        cleanDoc.select(".ads, .advertisment, .social").remove();
        cleanDoc.select("footer").remove();

        return cleanDoc.html();
    }

    private String buildOptimizedPrompt(String optimizedHtml) {
        return String.format("""
You are a CSS selector expert. Analyze this cleaned HTML from a news website and return ONLY a JSON object with CSS selectors.

HTML:
%s

Return JSON with these exact keys:
{
  "title": "best CSS selector for article title",
  "content": "best CSS selector for main article content", 
  "author": "best CSS selector for author name",
  "date": "best CSS selector for publish date"
}

Rules:
- Use null if element not found
- Prefer class/id selectors over complex hierarchies
- Choose selectors that would work for similar pages
- No explanations, only JSON
""", optimizedHtml);
    }

    private ContentCssSelector parseCssSelectorResponse(String responseText, String host) {
        try {
            // Extract JSON from response
            String jsonStr = responseText.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            ContentCssSelector result = new ContentCssSelector();
            result.setDomain(host);
            result.setTitle(getJsonValue(jsonNode, "title"));
            result.setContent(getJsonValue(jsonNode, "content"));
            result.setAuthor(getJsonValue(jsonNode, "author"));
            result.setPublishAt(getJsonValue(jsonNode, "date"));

            logger.info("Successfully parsed CSS selectors for domain: {}", host);
            return result;

        } catch (Exception e) {
            logger.error("Failed to parse Gemini response for domain: {}, response: {}", host, responseText, e);
            return createDefaultCssSelector(host);
        }
    }

    private String getJsonValue(JsonNode node, String key) {
        JsonNode valueNode = node.get(key);
        if (valueNode == null || valueNode.isNull() || valueNode.asText().equalsIgnoreCase("null")) {
            return null;
        }
        return valueNode.asText().trim();
    }

    private ContentCssSelector createDefaultCssSelector(String host) {
        ContentCssSelector result = new ContentCssSelector();
        result.setDomain(host);
        result.setTitle("h1");
        result.setContent(".content");
        result.setAuthor(".author");
        result.setPublishAt(".date");
        return result;
    }

    private String parseElementText(Document doc, String selector) {
        try {
            doc.select("script, style").remove();
            doc.select("head").remove();
            doc.select(".ads, .advertisment, .social").remove();
            doc.select("footer").remove();

            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                return elements.first().text().trim();
            }
        } catch (Exception e) {
            logger.debug("Failed to parse element with selector: {}", selector, e);
        }
        return null;
    }

    private int estimateTokens(String text) {
        return text.length() / CHARS_PER_TOKEN;
    }
}
