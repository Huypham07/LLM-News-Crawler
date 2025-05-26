package com.vdt.crawler.frontier_service.service;

import com.vdt.crawler.frontier_service.repository.DomainRepository;
import com.vdt.crawler.frontier_service.service.robotstxt.RobotstxtServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class FrontierServiceTest {

    @Mock
    private RobotstxtServer robotstxtServer;

    @Mock
    private DomainRepository domainRepository;
    private FrontierService frontierService;

    @BeforeEach
    public void setUp() throws IOException, InterruptedException {
        MockitoAnnotations.openMocks(this);

        // Mock robots.txt server
        when(robotstxtServer.allows(anyString())).thenReturn(true);
        when(robotstxtServer.getCrawlDelay(anyString())).thenReturn(1.0); // 1 second delay

        frontierService = new FrontierService(robotstxtServer, domainRepository);
    }

    @Test
    public void testAddSingleUrl() {
        String testUrl = "https://example.com/page1";

        frontierService.addToFrontier(testUrl);

        assertFalse(frontierService.isEmpty());

        String retrievedUrl = frontierService.getNextUrlFromFrontQueue();
        assertEquals(testUrl, retrievedUrl);
    }

    @Test
    public void testAddMultipleUrls() {
        List<String> testUrls = Arrays.asList(
                "https://example.com/page1",
                "https://example.com/page2",
                "https://example.com/page3"
        );

        frontierService.addToFrontier(testUrls);

        assertFalse(frontierService.isEmpty());

        // Get all URLs from front queue
        int count = 0;
        while (!frontierService.isEmpty() && count < 10) {
            String url = frontierService.getNextUrlFromFrontQueue();
            if (url != null) {
                assertTrue(testUrls.contains(url));
                count++;
            } else {
                break;
            }
        }

        assertEquals(3, count);
    }

    @Test
    public void testPriorityByDelay() throws IOException, InterruptedException {
        // Mock different crawl delays
        when(robotstxtServer.getCrawlDelay("https://fast.com/page1")).thenReturn(1.0);
        when(robotstxtServer.getCrawlDelay("https://slow.com/page1")).thenReturn(5.0);
        when(robotstxtServer.getCrawlDelay("https://medium.com/page1")).thenReturn(3.0);

        List<String> testUrls = Arrays.asList(
                "https://slow.com/page1",    // delay 5 - lowest priority
                "https://fast.com/page1",    // delay 1 - highest priority
                "https://medium.com/page1"   // delay 3 - medium priority
        );

        frontierService.addToFrontier(testUrls);

        // URLs should come out in order of priority (lowest delay first)
        String firstUrl = frontierService.getNextUrlFromFrontQueue();
        String secondUrl = frontierService.getNextUrlFromFrontQueue();
        String thirdUrl = frontierService.getNextUrlFromFrontQueue();

        assertEquals("https://fast.com/page1", firstUrl);
        assertEquals("https://medium.com/page1", secondUrl);
        assertEquals("https://slow.com/page1", thirdUrl);
    }

    @Test
    public void testBackQueueDistribution() {
        List<String> testUrls = Arrays.asList(
                "https://site1.com/page1",
                "https://site2.com/page1",
                "https://site1.com/page2",
                "https://site3.com/page1"
        );

        // Add URLs to front queue first
        frontierService.addToFrontier(testUrls);

        // Move to back queues
        for (int i = 0; i < testUrls.size(); i++) {
            String url = frontierService.getNextUrlFromFrontQueue();
            if (url != null) {
                frontierService.moveToBackQueue(url);
            }
        }

        // Get URLs from back queue
        int retrievedCount = 0;
        for (int i = 0; i < 10; i++) {
            String url = frontierService.getNextUrlFromBackQueue();
            if (url != null) {
                assertTrue(testUrls.contains(url));
                retrievedCount++;
            } else {
                break;
            }
        }

        assertEquals(testUrls.size(), retrievedCount);
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int urlsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Add URLs concurrently
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < urlsPerThread; i++) {
                        String url = "https://example" + threadId + ".com/page" + i;
                        frontierService.addToFrontier(url);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));

        // Verify all URLs were added
        Map<String, Object> stats = frontierService.getFrontierStats();
        int totalUrls = (Integer) stats.get("totalFrontUrls");
        assertEquals(threadCount * urlsPerThread, totalUrls);

        executor.shutdown();
    }

    @Test
    public void testRobotsBlocking() throws IOException, InterruptedException {
        String blockedUrl = "https://blocked.com/page1";
        String allowedUrl = "https://allowed.com/page1";

        // Mock robots.txt responses
        when(robotstxtServer.allows(blockedUrl)).thenReturn(false);
        when(robotstxtServer.allows(allowedUrl)).thenReturn(true);

        frontierService.addToFrontier(Arrays.asList(blockedUrl, allowedUrl));

        // Only allowed URL should be in queue
        String retrievedUrl = frontierService.getNextUrlFromFrontQueue();
        assertEquals(allowedUrl, retrievedUrl);

        // Queue should be empty now (blocked URL was not added)
        String nextUrl = frontierService.getNextUrlFromFrontQueue();
        assertNull(nextUrl);
    }

    @Test
    public void testEmptyQueue() {
        assertTrue(frontierService.isEmpty());

        String url = frontierService.getNextUrlFromFrontQueue();
        assertNull(url);

        url = frontierService.getNextUrlFromBackQueue();
        assertNull(url);
    }

    @Test
    public void testClearQueue() {
        List<String> testUrls = Arrays.asList(
                "https://example.com/page1",
                "https://example.com/page2"
        );

        frontierService.addToFrontier(testUrls);
        assertFalse(frontierService.isEmpty());

        frontierService.clear();
        assertTrue(frontierService.isEmpty());
    }

    @Test
    public void testStatistics() {
        List<String> testUrls = Arrays.asList(
                "https://site1.com/page1",
                "https://site2.com/page1",
                "https://site1.com/page2"
        );

        frontierService.addToFrontier(testUrls);

        Map<String, Object> stats = frontierService.getFrontierStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalFrontUrls"));
        assertTrue(stats.containsKey("totalBackUrls"));
        assertTrue(stats.containsKey("frontQueues"));
        assertTrue(stats.containsKey("backQueues"));

        assertEquals(3, (Integer) stats.get("totalFrontUrls"));
    }

    @Test
    public void testInvalidUrls() {
        List<String> invalidUrls = Arrays.asList(
                null,
                "",
                "   ",
                "not-a-url",
                "ftp://invalid-protocol.com"
        );

        // These should not crash the service
        for (String url : invalidUrls) {
            frontierService.addToFrontier(url);
        }

        // Queue should still be empty
        assertFalse(frontierService.isEmpty());
    }
}