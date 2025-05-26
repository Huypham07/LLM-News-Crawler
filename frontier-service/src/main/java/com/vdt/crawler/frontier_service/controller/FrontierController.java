package com.vdt.crawler.frontier_service.controller;

import com.vdt.crawler.frontier_service.service.FrontierConsumer;
import com.vdt.crawler.frontier_service.service.FrontierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/frontier")
public class FrontierController {

    private static final Logger logger = LoggerFactory.getLogger(FrontierController.class);

    private final FrontierService frontierService;
    private final FrontierConsumer frontierConsumer;

    @Autowired
    public FrontierController(FrontierService frontierService, FrontierConsumer frontierConsumer) {
        this.frontierService = frontierService;
        this.frontierConsumer = frontierConsumer;
    }

    /**
     * Add seed URLs to frontier
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> addSeedUrls(@RequestBody List<String> seedUrls) {
        try {
            logger.info("Received {} seed URLs via REST API", seedUrls.size());

            frontierConsumer.handleSeedUrls(seedUrls);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Seed URLs added successfully");
            response.put("count", seedUrls.size());
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding seed URLs", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to add seed URLs: " + e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Add single URL to frontier
     */
    @PostMapping("/url")
    public ResponseEntity<Map<String, Object>> addUrl(@RequestBody Map<String, String> request) {
        try {
            String url = request.get("url");
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("URL is required");
            }

            frontierService.addToFrontier(url.trim());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "URL added successfully");
            response.put("url", url);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error adding URL", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get next URL from front queue
     */
    @GetMapping("/next/front")
    public ResponseEntity<Map<String, Object>> getNextFromFrontQueue() {
        try {
            String url = frontierService.getNextUrlFromFrontQueue();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("url", url);
            response.put("timestamp", System.currentTimeMillis());

            if (url != null) {
                // Move to back queue for politeness
                frontierService.moveToBackQueue(url);
                response.put("moved_to_back_queue", true);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting next URL from front queue", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get next URL from back queue
     */
    @GetMapping("/next/back")
    public ResponseEntity<Map<String, Object>> getNextFromBackQueue() {
        try {
            String url = frontierService.getNextUrlFromBackQueue();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("url", url);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting next URL from back queue", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get frontier statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getFrontierStats() {
        try {
            Map<String, Object> stats = frontierService.getFrontierStats();
            stats.put("timestamp", System.currentTimeMillis());
            stats.put("status", "success");

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            logger.error("Error getting frontier stats", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Check if frontier is empty
     */
    @GetMapping("/empty")
    public ResponseEntity<Map<String, Object>> isFrontierEmpty() {
        try {
            boolean isEmpty = frontierService.isEmpty();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("empty", isEmpty);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error checking if frontier is empty", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Clear all frontier queues
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearFrontier() {
        try {
            frontierService.clear();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "All frontier queues cleared");
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error clearing frontier", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "frontier-service");
        response.put("timestamp", System.currentTimeMillis());

        // Add basic stats
        try {
            Map<String, Object> stats = frontierService.getFrontierStats();
            response.put("totalUrls",
                    (Integer) stats.get("totalFrontUrls") + (Integer) stats.get("totalBackUrls"));
        } catch (Exception e) {
            logger.warn("Could not get stats for health check", e);
        }

        return ResponseEntity.ok(response);
    }
}