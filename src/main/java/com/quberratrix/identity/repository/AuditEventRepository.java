package com.quberratrix.identity.repository;

import com.quberratrix.identity.entity.AuditEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface AuditEventRepository extends ReactiveCrudRepository<AuditEvent, UUID> {
}
