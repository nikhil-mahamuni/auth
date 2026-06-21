package com.quberratrix.identity.jwt;

import com.quberratrix.identity.repository.RevokedAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevokedTokenValidator {

    private final RevokedAccessTokenRepository revokedAccessTokenRepository;

    public Mono<Boolean> isTokenRevoked(String jti) {
        if (jti == null) return Mono.just(false);
        try {
            return revokedAccessTokenRepository.existsByJti(UUID.fromString(jti));
        } catch (IllegalArgumentException e) {
            return Mono.just(true); // Invalid JTI format
        }
    }
}
