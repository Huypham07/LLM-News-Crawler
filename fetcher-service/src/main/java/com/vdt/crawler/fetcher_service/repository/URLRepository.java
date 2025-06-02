package com.vdt.crawler.fetcher_service.repository;

import com.vdt.crawler.fetcher_service.model.URLMetaData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface URLRepository extends MongoRepository<URLMetaData, String> {

}
