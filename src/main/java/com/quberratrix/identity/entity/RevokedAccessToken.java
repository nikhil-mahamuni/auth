package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("identity_revoked_access_tokens")
public class RevokedAccessToken extends PersistableEntity {
    @Id
    private UUID id;
    private UUID jti;
    private UUID userId;
    private Instant revokedAt;
    private Instant expiresAt;
}
