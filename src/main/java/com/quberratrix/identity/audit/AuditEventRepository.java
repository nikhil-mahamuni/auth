package com.quberratrix.identity.audit;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface AuditEventRepository extends ReactiveCrudRepository<AuditEvent, UUID> {
}
