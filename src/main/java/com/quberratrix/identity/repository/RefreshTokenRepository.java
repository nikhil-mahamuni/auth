package com.quberratrix.identity.repository;

import com.quberratrix.identity.entity.RefreshToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, UUID> {
    Mono<RefreshToken> findByTokenHash(String tokenHash);
    Flux<RefreshToken> findByFamilyId(UUID familyId);
    Mono<Void> deleteBySessionId(UUID sessionId);
}
