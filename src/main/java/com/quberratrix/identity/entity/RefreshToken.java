package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("identity_refresh_tokens")
public class RefreshToken extends PersistableEntity {
    @Id
    private UUID id;
    private String tokenHash;
    private UUID sessionId;
    private UUID userId;
    private UUID clientId;
    private Instant issuedAt;
    private Instant expiresAt;
    private boolean revoked;
    private UUID familyId;
    private String status;
    private Instant lastUsedAt;
    private Instant rotatedAt;
    private Instant revokedAt;
    private String revokedBy;
    private String revokeReason;
}
