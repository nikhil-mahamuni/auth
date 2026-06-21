package com.quberratrix.identity.tokens;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PasswordResetTokenRepository extends ReactiveCrudRepository<PasswordResetToken, UUID> {
    Mono<PasswordResetToken> findByTokenHash(String tokenHash);
}
