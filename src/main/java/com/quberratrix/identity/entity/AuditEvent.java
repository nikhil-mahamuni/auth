package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("identity_audit_events")
public class AuditEvent extends PersistableEntity {
    @Id
    private UUID id;
    private String eventType;
    private UUID actorId;
    private UUID targetId;
    private UUID clientId;
    private UUID sessionId;
    private String ipAddress;
    private String userAgent;
    private String metadata; // We will handle JSON mapping by simply storing stringified JSON for now
    private Instant createdAt;
}
