package com.vdt.crawler.frontier_service.service;

import com.vdt.crawler.frontier_service.metric.FrontierMetrics;
import com.vdt.crawler.frontier_service.model.Domain;
import com.vdt.crawler.frontier_service.repository.DomainRepository;
import com.vdt.crawler.frontier_service.service.robotstxt.RobotstxtServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class FrontierService {
    private final RobotstxtServer robotstxtServer;
    private final DomainRepository domainRepository;
    private final Map<String, Domain> domainsDataCache;
    private final FrontierMetrics frontierMetrics;

    private final Logger logger = LoggerFactory.getLogger(FrontierService.class);

    // Front queues - Priority queue
    private final Map<Integer, BlockingQueue<UrlWithTimestamp>> frontQueues;

    // Back queues - Domain based for politeness
    private final Map<String, BlockingQueue<String>> backQueues;

    // Mapping table for domain to back queue assignment
    private final Map<String, String> domainToBackQueueMapping;

    // Read-write locks for thread safety
    private final ReadWriteLock frontQueueLock = new ReentrantReadWriteLock();
    private final ReadWriteLock backQueueLock = new ReentrantReadWriteLock();
    private final ReadWriteLock mappingLock = new ReentrantReadWriteLock();

    private final ConcurrentSkipListSet<String> retryUrlsSet = new ConcurrentSkipListSet<>();
    // Queue selectors
    private volatile String currentBackQueue = null;

    // Configuration
    private static final int MAX_QUEUE_SIZE = 10000;
    private static final int NUMBER_OF_BACK_QUEUES = 10;

    private final AtomicInteger currentBackQueueIndex = new AtomicInteger(0);

    // Weighted round-robin schedule: more weight = more frequent access
    private final List<Integer> weightedSchedule = List.of(3, 3, 3, 3, 3, 2, 2, 2, 1); // priority levels
    private final AtomicInteger currentScheduleIndex = new AtomicInteger(0); // Thread-safe counter

    @Autowired
    public FrontierService(RobotstxtServer robotstxtServer, DomainRepository domainRepository, FrontierMetrics frontierMetrics) {
        this.robotstxtServer = robotstxtServer;
        this.domainRepository = domainRepository;
        this.frontierMetrics = frontierMetrics;
        this.domainsDataCache = new ConcurrentHashMap<>();
        this.frontQueues = new ConcurrentHashMap<>();
        this.backQueues = new ConcurrentHashMap<>();
        this.domainToBackQueueMapping = new ConcurrentHashMap<>();

        // Initialize back queues
        for (int i = 0; i < NUMBER_OF_BACK_QUEUES; i++) {
            backQueues.put("b" + i, new LinkedBlockingQueue<>(MAX_QUEUE_SIZE));
        }

        logger.info("FrontierService initialized with {} back queues", NUMBER_OF_BACK_QUEUES);
    }

    public void addToFrontier(String url) {
        if (url == null || url.trim().isEmpty()) {
            logger.warn("Empty or null URL provided");
            return;
        }

        try {
            processUrl(url);
        } catch (Exception e) {
            logger.error("Error processing URL: {}", url, e);
        }
    }

    public void addToFrontier(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            logger.warn("Empty or null URL list provided");
            return;
        }

        // Process URLs in parallel for better performance
        urls.parallelStream().forEach(url -> {
            try {
                if (url == null || url.trim().isEmpty()) {
                    logger.warn("Empty or null URL provided");
                } else {
                    processUrl(url);
                }
            } catch (Exception e) {
                logger.error("Error processing URL: {}", url, e);
            }
        });
    }

    private void processUrl(String url) {
        try {
            String host = new URL(url).getHost();

            // Check robots.txt

            Double crawlDelay_double = robotstxtServer.getCrawlDelay(url);
            int crawlDelay = crawlDelay_double != null ? crawlDelay_double.intValue() : 2;

            Domain domain = domainsDataCache.computeIfAbsent(host, h ->
                    domainRepository.findByDomain(h).orElse(null)
            );

            if (domain == null) {
                frontierMetrics.incrementRejectedUrls();
                logger.warn("Drop {} in domain {}", url, host);
                return;
            }

            if (!robotstxtServer.allows(url)) {
                frontierMetrics.incrementRejectedUrls(host);
                logger.info("URL blocked by robots.txt: {}", url);
                return;
            }

            int priority = domain.getPriority();
            Instant lastCrawl = domain.getLastCrawled();

            // Add to appropriate front queue based on crawl delay
            if (addToFrontQueue(host, url, priority, lastCrawl, crawlDelay)) {
                frontierMetrics.incrementScheduledUrlsTotal();
                logger.info("Added URL to frontier: {} with crawl delay: {}", url, crawlDelay);
            } else {
                frontierMetrics.incrementRejectedUrls(host);
            }
        } catch (InterruptedException | IOException e) {
            frontierMetrics.incrementRejectedUrls();
            logger.error("Error processing URL: {}", url, e);
        }

    }

    private String extractDomain(String url) throws MalformedURLException {
        URL urlObj = new URL(url);
        return urlObj.getHost().toLowerCase();
    }

    public void addRetryUrl(String url) {
        retryUrlsSet.add(url);
    }

    public String getNextUrlfromRetrySet() {
        if (retryUrlsSet.isEmpty()) return null;
        String url = retryUrlsSet.first();
        retryUrlsSet.remove(url);
        return url;
    }

    public static class UrlWithTimestamp implements Comparable<UrlWithTimestamp> {
        private final String url;
        private final Instant lastCrawled;

        public UrlWithTimestamp(String url, Instant lastCrawled) {
            this.url = url;
            this.lastCrawled = lastCrawled;
        }

        @Override
        public int compareTo(UrlWithTimestamp other) {
            if (this.lastCrawled == null && other.lastCrawled == null) {
                return 0;
            }
            if (this.lastCrawled == null) {
                return -1;
            }
            if (other.lastCrawled == null) {
                return 1;
            }
            return this.lastCrawled.compareTo(other.lastCrawled);
        }


        public String getUrl() { return url; }
        public Instant getLastCrawled() { return lastCrawled; }
    }

    private boolean addToFrontQueue(String domain, String url, int priority, Instant lastCrawled, int crawlDelay) {
        frontQueueLock.writeLock().lock();
        try {
            int baseQueueKey = priority;
            // Add domain hash to distribute URLs across different queues
            int domainHash = Math.abs(domain.hashCode() % 3); // Spread across 3 sub-queues
            int queueKey = baseQueueKey * 10 + domainHash; // e.g., priority 5 -> queues 50, 51, 52

            UrlWithTimestamp urlItem = new UrlWithTimestamp(url, lastCrawled);

            // Try primary queue first
            if (tryAddToQueue(queueKey, urlItem)) {
                return true;
            }

            // If failed, try other sub-queues with same base priority
            for (int i = 0; i < 3; i++) {
                if (i != domainHash) {
                    int alternativeKey = baseQueueKey * 10 + i;
                    if (tryAddToQueue(alternativeKey, urlItem)) {
                        logger.debug("Added URL to alternative queue {}: {}", alternativeKey, url);
                        return true;
                    }
                }
            }

            // If still failed, try lower priority queues
            for (int fallbackBase = baseQueueKey - 1; fallbackBase >= baseQueueKey - 2; fallbackBase--) {
                for (int i = 0; i < 3; i++) {
                    int fallbackKey = fallbackBase * 10 + i;
                    if (tryAddToQueue(fallbackKey, urlItem)) {
                        logger.info("Added URL to fallback queue {}: {}", fallbackKey, url);
                        return true;
                    }
                }
            }

            logger.warn("All queues full, dropping URL: {}", url);
            return false;
        } finally {
            frontQueueLock.writeLock().unlock();
        }
    }

    private boolean tryAddToQueue(int queueKey, UrlWithTimestamp urlItem) {
        BlockingQueue<UrlWithTimestamp> queue = frontQueues.computeIfAbsent(
                queueKey,
                k -> new PriorityBlockingQueue<>(MAX_QUEUE_SIZE)
        );
        return queue.offer(urlItem);
    }

    /**
     * Get next URL from front queue (prioritized by crawl delay) weight round robin
     */
    public String getNextUrlFromFrontQueue() {
        frontQueueLock.writeLock().lock();
        try {
            int trials = weightedSchedule.size();
            int startIndex = currentScheduleIndex.get();
            while (trials-- > 0) {
                int priorityLevel = weightedSchedule.get(currentScheduleIndex.get());
                currentScheduleIndex.updateAndGet(i -> (i + 1) % weightedSchedule.size());

                // Lặp qua 3 sub-queues cùng priority
                int startSubQueue = (int) (System.nanoTime() % 3);
                for (int j = 0; j < 3; j++) {
                    int i = (startSubQueue + j) % 3;
                    int queueKey = priorityLevel * 10 + i;
                    BlockingQueue<UrlWithTimestamp> queue = frontQueues.get(queueKey);

                    if (queue != null && !queue.isEmpty()) {
                        UrlWithTimestamp item = queue.poll();
                        if (item != null) {
                            logger.debug("Retrieved URL from queue {} (priority {}): {}",
                                    queueKey, priorityLevel, item.getUrl());
                            return item.getUrl();
                        }
                    }
                }
            }

            logger.debug("No URLs available in any front queue");
            return null;
        } finally {
            frontQueueLock.writeLock().unlock();
        }
    }

    /**
     * Move URL from front queue to back queue for politeness
     */
    public void moveToBackQueue(String url) {
        try {
            String domain = extractDomain(url);
            String backQueueId = getBackQueueForDomain(domain);

            backQueueLock.writeLock().lock();
            try {
                BlockingQueue<String> backQueue = backQueues.get(backQueueId);
                if (backQueue != null) {
                    if (!backQueue.offer(url)) {
                        logger.warn("Back queue {} full, dropping URL: {}", backQueueId, url);
                    }
                } else {
                    logger.error("Back queue not found: {}", backQueueId);
                }
            } finally {
                backQueueLock.writeLock().unlock();
            }

        } catch (MalformedURLException e) {
            logger.error("Invalid URL format: {}", url, e);
        }
    }

    private String getBackQueueForDomain(String domain) {
        return domainToBackQueueMapping.computeIfAbsent(domain, k -> {
            // Assign domain to back queue using hash for even distribution
            int queueIndex = Math.abs(domain.hashCode() % NUMBER_OF_BACK_QUEUES);
            return "b" + (queueIndex);
        });
    }

    /**
     * Get next URL from back queue (round-robin selection)
     */
    public String getNextUrlFromBackQueue() {
        backQueueLock.writeLock().lock();
        try {
            List<String> queueIds = new ArrayList<>(backQueues.keySet());
            Collections.sort(queueIds);

            int total = queueIds.size();
            for (int i = 0; i < total; i++) {
                int index = (currentBackQueueIndex.get() + i) % total;
                String queueId = queueIds.get(index);

                BlockingQueue<String> queue = backQueues.get(queueId);
                if (queue != null && !queue.isEmpty()) {
                    currentBackQueueIndex.set((index + 1) % total);
                    return queue.poll();
                }
            }

            return null;
        } finally {
            backQueueLock.writeLock().unlock();
        }
    }

    /**
     * Get frontier statistics
     */
    public Map<String, Object> getFrontierStats() {
        Map<String, Object> stats = new HashMap<>();

        // Front queue stats
        frontQueueLock.readLock().lock();
        try {
            Map<String, Integer> frontQueueSizes = new HashMap<>();
            int totalFrontUrls = 0;

            for (Map.Entry<Integer, BlockingQueue<UrlWithTimestamp>> entry : frontQueues.entrySet()) {
                int size = entry.getValue().size();
                frontQueueSizes.put("f" + entry.getKey(), size);
                totalFrontUrls += size;
            }

            stats.put("frontQueues", frontQueueSizes);
            stats.put("totalFrontUrls", totalFrontUrls);
        } finally {
            frontQueueLock.readLock().unlock();
        }

        // Back queue stats
        backQueueLock.readLock().lock();
        try {
            Map<String, Integer> backQueueSizes = new HashMap<>();
            int totalBackUrls = 0;

            for (Map.Entry<String, BlockingQueue<String>> entry : backQueues.entrySet()) {
                int size = entry.getValue().size();
                backQueueSizes.put(entry.getKey(), size);
                totalBackUrls += size;
            }

            stats.put("backQueues", backQueueSizes);
            stats.put("totalBackUrls", totalBackUrls);
        } finally {
            backQueueLock.readLock().unlock();
        }

        // Domain mapping stats
        mappingLock.readLock().lock();
        try {
            stats.put("totalDomainMappings", domainToBackQueueMapping.size());
        } finally {
            mappingLock.readLock().unlock();
        }

        return stats;
    }

    /**
     * Check if frontier is empty
     */
    public boolean isEmpty() {
        frontQueueLock.readLock().lock();
        try {
            boolean frontEmpty = frontQueues.values().stream()
                    .allMatch(Queue::isEmpty);

            if (frontEmpty) {
                backQueueLock.readLock().lock();
                try {
                    return backQueues.values().stream()
                            .allMatch(Queue::isEmpty);
                } finally {
                    backQueueLock.readLock().unlock();
                }
            }

            return false;
        } finally {
            frontQueueLock.readLock().unlock();
        }
    }

    /**
     * Clear all queues
     */
    public void clear() {
        frontQueueLock.writeLock().lock();
        try {
            frontQueues.values().forEach(BlockingQueue::clear);
        } finally {
            frontQueueLock.writeLock().unlock();
        }

        backQueueLock.writeLock().lock();
        try {
            backQueues.values().forEach(BlockingQueue::clear);
        } finally {
            backQueueLock.writeLock().unlock();
        }

        mappingLock.writeLock().lock();
        try {
            domainToBackQueueMapping.clear();
        } finally {
            mappingLock.writeLock().unlock();
        }

        logger.info("All frontier queues cleared");
    }
}