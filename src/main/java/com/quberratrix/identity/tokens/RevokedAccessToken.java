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
@Table("identity_revoked_access_tokens")
public class RevokedAccessToken extends PersistableEntity {
    @Id
    private UUID id;
    private UUID jti;
    private UUID userId;
    private Instant revokedAt;
    private Instant expiresAt;
}
