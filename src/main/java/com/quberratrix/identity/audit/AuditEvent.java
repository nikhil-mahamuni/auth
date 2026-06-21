package com.quberratrix.identity.audit;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
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
    private String correlationId;
    private String requestId;
    private String metadata;
    private Instant createdAt;
}
