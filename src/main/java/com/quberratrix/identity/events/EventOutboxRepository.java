package com.quberratrix.identity.events;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface EventOutboxRepository extends ReactiveCrudRepository<EventOutbox, UUID> {
}
