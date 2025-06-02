package com.vdt.crawler.fetcher_service.controller;

import com.vdt.crawler.fetcher_service.model.URLMetaData;
import com.vdt.crawler.fetcher_service.repository.URLRepository;
import com.vdt.crawler.fetcher_service.service.FetcherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.vdt.crawler.fetcher_service.util.UrlHashUtil;

@RestController
@RequestMapping("/api/fetcher")
public class FetcherController {
    private final URLRepository urlRepository;
    private final FetcherService fetcherService;

    @Autowired
    public FetcherController(URLRepository urlRepository, FetcherService fetcherService) {
        this.urlRepository = urlRepository;
        this.fetcherService = fetcherService;
    }

    @GetMapping("/{url}")
    public ResponseEntity<URLMetaData> getUrlMetaData(@PathVariable String url) {
        return urlRepository.findById(UrlHashUtil.generateUrlHash(url))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
