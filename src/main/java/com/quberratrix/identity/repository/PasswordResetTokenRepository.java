package com.quberratrix.identity.repository;

import com.quberratrix.identity.entity.PasswordResetToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PasswordResetTokenRepository extends ReactiveCrudRepository<PasswordResetToken, UUID> {
    Mono<PasswordResetToken> findByTokenHash(String tokenHash);
}
