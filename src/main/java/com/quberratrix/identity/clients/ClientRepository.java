package com.quberratrix.identity.clients;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ClientRepository extends ReactiveCrudRepository<Client, UUID> {
    Mono<Client> findByClientId(String clientId);
}
