package com.vdt.crawler.frontier_service.service;

import com.vdt.crawler.frontier_service.model.Domain;
import com.vdt.crawler.frontier_service.repository.DomainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(SchedulerService.class);

    private final DomainRepository domainRepository;
    private final FrontierService frontierService;

    public SchedulerService(DomainRepository domainRepository, FrontierService frontierService) {
        this.domainRepository = domainRepository;
        this.frontierService = frontierService;
    }


    @Scheduled(initialDelay = 30000, fixedRate = 30000) // 20 minutes = 1200000 milliseconds
    public void scheduleCrawling() throws MalformedURLException {
        logger.info(">>> Starting scheduled Crawling task ...");
        List<Domain> activeDomains = domainRepository.findByActiveTrue();

        List<String> seedUrls = new ArrayList<>();
        activeDomains.forEach(domain -> {
            if (domain.getSeedUrls() != null) {
                seedUrls.addAll(domain.getSeedUrls());
            }
        });

        logger.info(">>> Found total {} active domains and {} urls from seedUrls", activeDomains.size(), seedUrls.size());
        frontierService.addToFrontier(seedUrls);
    }

    private void scheduleDomainCrawl(Domain domain) {
        logger.info(">>> Starting scheduled Crawling task for domain {} ...", domain.getDomain());
        if (domain.getSeedUrls() != null) {
            logger.info(">>> Found total {} urls from seedUrls", domain.getSeedUrls().size());
            frontierService.addToFrontier(domain.getSeedUrls());
        }
    }

    // Manual trigger for specific domain
    public void scheduleDomainCrawlManually(String domainName) {
        Domain domain = domainRepository.findByDomain(domainName)
                .orElseThrow(() -> new RuntimeException("Domain not found: " + domainName));

        scheduleDomainCrawl(domain);
    }
}
