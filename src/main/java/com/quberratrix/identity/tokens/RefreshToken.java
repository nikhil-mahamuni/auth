package com.quberratrix.identity.tokens;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_refresh_tokens")
public class RefreshToken extends PersistableEntity {
    @Id
    private UUID id;
    private String tokenHash;
    private UUID familyId;
    private UUID sessionId;
    private UUID userId;
    private UUID clientId;
    private String status;
    private Instant issuedAt;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private Instant rotatedAt;
    private Instant revokedAt;
    private String revokedBy;
    private String revokeReason;
}
