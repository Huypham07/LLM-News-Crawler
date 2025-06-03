package com.vdt.crawler.frontier_service.service;

import com.vdt.crawler.frontier_service.metric.FrontierMetrics;
import com.vdt.crawler.frontier_service.repository.DomainRepository;
import com.vdt.crawler.frontier_service.service.robotstxt.RobotstxtServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
public class FrontierServiceTest {

    @Mock
    private RobotstxtServer robotstxtServer;

    @Autowired
    private DomainRepository domainRepository;

    @Mock
    private FrontierMetrics frontierMetrics;
    /**
     * db must have vnexpress.net, dantri.com.vn, thanhnien.vn
     */

    private FrontierService frontierService;

    @BeforeEach
    public void setUp() throws IOException, InterruptedException {
        MockitoAnnotations.openMocks(this);

        // Mock robots.txt server
        when(robotstxtServer.allows(anyString())).thenReturn(true);
        when(robotstxtServer.getCrawlDelay(anyString())).thenReturn(1.0); // 1 second delay

        frontierService = new FrontierService(robotstxtServer, domainRepository, frontierMetrics);
    }

    @Test
    public void testAddSingleUrl() {
        String testUrl = "https://vnexpress.net/kinh-doanh";

        frontierService.addToFrontier(testUrl);

        assertFalse(frontierService.isEmpty());

        String retrievedUrl = frontierService.getNextUrlFromFrontQueue();
        assertEquals(testUrl, retrievedUrl);
    }

    @Test
    public void testAddMultipleUrls() {
        List<String> testUrls = Arrays.asList(
                "https://vnexpress.net/thoi-su",
                "https://vnexpress.net/the-gioi",
                "https://slow.com/page1"
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

        assertEquals(2, count);
    }


    @Test
    public void testBackQueueDistribution() {
        List<String> testUrls = Arrays.asList(
                "https://dantri.com.vn/tin-moi-nhat.htm",
                "https://vnexpress.net/kinh-doanh",
                "https://thanhnien.vn/chinh-tri.htm"
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
    public void testRobotsBlocking() throws IOException, InterruptedException {
        String blockedUrl = "https://dantri.com.vn/tin-moi-nhat.htm";
        String allowedUrl = "https://vnexpress.net/kinh-doanh";

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
                "https://vnexpress.net/kinh-doanh",
                "https://vnexpress.net/thoi-su"
        );

        frontierService.addToFrontier(testUrls);
        assertFalse(frontierService.isEmpty());

        frontierService.clear();
        assertTrue(frontierService.isEmpty());
    }

    @Test
    public void testStatistics() {
        List<String> testUrls = Arrays.asList(
                "https://vnexpress.net/thoi-su",
                "https://vnexpress.net/the-gioi",
                "https://slow.com/page1"
        );

        frontierService.addToFrontier(testUrls);

        Map<String, Object> stats = frontierService.getFrontierStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalFrontUrls"));
        assertTrue(stats.containsKey("totalBackUrls"));
        assertTrue(stats.containsKey("frontQueues"));
        assertTrue(stats.containsKey("backQueues"));

        assertEquals(2, (Integer) stats.get("totalFrontUrls"));
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
        assertTrue(frontierService.isEmpty());
    }
}