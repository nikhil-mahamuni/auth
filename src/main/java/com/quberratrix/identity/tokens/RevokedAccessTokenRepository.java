package com.quberratrix.identity.tokens;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RevokedAccessTokenRepository extends ReactiveCrudRepository<RevokedAccessToken, UUID> {
    Mono<Boolean> existsByJti(UUID jti);
}
