package com.blockstats.tax.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AiLabelingPreferenceRepository extends ReactiveMongoRepository<AiLabelingPreferenceEntity, String> {
    Mono<AiLabelingPreferenceEntity> findByUserId(String userId);
}
