package com.quberratrix.identity.providers;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ProviderConfigRepository extends ReactiveCrudRepository<ProviderConfig, UUID> {
    Mono<ProviderConfig> findByProviderKey(String providerKey);
}
