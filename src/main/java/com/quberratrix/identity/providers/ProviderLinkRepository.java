package com.quberratrix.identity.providers;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProviderLinkRepository extends ReactiveCrudRepository<ProviderLink, UUID> {
    Flux<ProviderLink> findByUserId(UUID userId);
    Mono<ProviderLink> findByProviderIdAndProviderUserId(UUID providerId, String providerUserId);
}
