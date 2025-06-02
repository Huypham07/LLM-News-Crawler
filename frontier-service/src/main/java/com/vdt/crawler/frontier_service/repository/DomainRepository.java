package com.vdt.crawler.frontier_service.repository;

import com.vdt.crawler.frontier_service.model.Domain;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface DomainRepository extends MongoRepository<Domain, String> {
    List<Domain> findByActiveTrue();
    Optional<Domain> findByDomain(String domain);
}
