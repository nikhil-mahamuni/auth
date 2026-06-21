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
@Table("identity_password_reset_tokens")
public class PasswordResetToken extends PersistableEntity {
    @Id
    private UUID id;
    private String tokenHash;
    private UUID userId;
    private Instant issuedAt;
    private Instant expiresAt;
    private boolean used;
}
