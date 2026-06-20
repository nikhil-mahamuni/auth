package com.quberratrix.identity.repository;

import com.quberratrix.identity.entity.Session;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface SessionRepository extends ReactiveCrudRepository<Session, UUID> {
    Flux<Session> findByUserIdAndStatus(UUID userId, String status);
}
