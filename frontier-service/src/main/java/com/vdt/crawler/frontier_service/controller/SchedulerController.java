package com.vdt.crawler.frontier_service.controller;

import com.vdt.crawler.frontier_service.model.Domain;
import com.vdt.crawler.frontier_service.repository.DomainRepository;
import com.vdt.crawler.frontier_service.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SchedulerController {
    private final DomainRepository domainRepository;
    private final SchedulerService schedulerService;

    @Autowired
    public SchedulerController(DomainRepository domainRepository, SchedulerService schedulerService) {
        this.domainRepository = domainRepository;
        this.schedulerService = schedulerService;
    }

    @GetMapping("/domains")
    public List<Domain> getAllDomains() {
        return domainRepository.findAll();
    }

    @GetMapping("/domains/active")
    public List<Domain> getActiveDomains() {
        return domainRepository.findByActiveTrue();
    }


    @GetMapping("/domains/{domain}")
    public ResponseEntity<Domain> getDomainByName(@PathVariable String domain) {
        return domainRepository.findByDomain(domain)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/domains")
    public Domain createDomain(@RequestBody Domain domain) {
        return domainRepository.save(domain);
    }

    @PutMapping("/domains/{domain}")
    public ResponseEntity<Domain> updateDomain(@PathVariable String domain, @RequestBody Domain domainDetails) {
        return domainRepository.findByDomain(domain)
                .map(domainObj -> {
                    if (domainDetails.getDomain() != null) {
                        domainObj.setDomain(domainDetails.getDomain());
                    }
                    if (domainDetails.getSeedUrls() != null) {
                        domainObj.setSeedUrls(domainDetails.getSeedUrls());
                    }
                    if (domainDetails.getActive() != null) {
                        domainObj.setActive(domainDetails.getActive());
                    }
                    if (domainDetails.getLastCrawled() != null) {
                        domainObj.setLastCrawled(domainDetails.getLastCrawled());
                    }
                    domainDetails.setPriority(domainObj.getPriority());
                    return ResponseEntity.ok(domainRepository.save(domainObj));
                })
                .orElse(ResponseEntity.notFound().build());

    }

    @DeleteMapping("/domains/{domain}")
    public ResponseEntity<?> deleteDomain(@PathVariable String domain) {
        return domainRepository.findByDomain(domain)
                .map(domainObj -> {
                    domainRepository.delete(domainObj);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/trigger")
    public ResponseEntity<String> triggerManualCrawl() {
        try {
            schedulerService.scheduleCrawling();
            return ResponseEntity.ok("Manual crawl triggered successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error triggering crawl: " + e.getMessage());
        }
    }

    @PostMapping("/trigger/{domain}")
    public ResponseEntity<String> triggerDomainCrawl(@PathVariable String domain) {
        try {
            schedulerService.scheduleDomainCrawlManually(domain);
            return ResponseEntity.ok("Manual crawl triggered for domain: " + domain);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error triggering crawl for domain: " + e.getMessage());
        }
    }
}
