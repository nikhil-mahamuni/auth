package com.quberratrix.identity.tokens;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EmailVerificationTokenRepository extends ReactiveCrudRepository<EmailVerificationToken, UUID> {
    Mono<EmailVerificationToken> findByTokenHash(String tokenHash);
}
