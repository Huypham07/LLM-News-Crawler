package com.vdt.crawler.frontier_service.service;

import com.vdt.crawler.frontier_service.config.SchedulerConfig;
import com.vdt.crawler.frontier_service.model.Domain;
import com.vdt.crawler.frontier_service.repository.DomainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DataInitializationService implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DataInitializationService.class);

    private final DomainRepository domainRepository;
    private final SchedulerConfig config;

    @Autowired
    public DataInitializationService(DomainRepository domainRepository, SchedulerConfig config) {
        this.domainRepository = domainRepository;
        this.config = config;
    }

    @Override
    public void run(String... args) {
        List<String> configuredDomains = config.getDomains();

        if (configuredDomains == null || configuredDomains.isEmpty()) {
            logger.warn("No domains configured in application.yml");
            return;
        }

        // get all domains in DB
        List<String> existingDomains = domainRepository.findAll()
                .stream()
                .map(Domain::getDomain)
                .toList();

        // find new domain in config
        List<String> newDomains = configuredDomains.stream()
                .filter(domain -> !existingDomains.contains(domain))
                .toList();

        if (!newDomains.isEmpty()) {
            List<Domain> domainsToSave = newDomains.stream()
                    .map(this::createDomain)
                    .collect(Collectors.toList());

            domainRepository.saveAll(domainsToSave);
            logger.info("Added {} domain(s)", newDomains.size());
        } else {
            logger.info("All configured domains already exist in the database.");
        }
    }

    private Domain createDomain(String domainName, List<String> seedUrls, int priority) {
        Domain domain = new Domain();
        domain.setDomain(domainName);
        domain.setSeedUrls(seedUrls);
        domain.setPriority(priority);
        return domain;
    }

    private Domain createDomain(String domainName) {
        Domain domain = new Domain();
        domain.setDomain(domainName);
        List<String> seedUrls = new ArrayList<>();
        seedUrls.add("https://" + domainName);
        domain.setSeedUrls(seedUrls);
        domain.setPriority(1);
        domain.setCreateAt(Instant.now());
        return domain;
    }
}
