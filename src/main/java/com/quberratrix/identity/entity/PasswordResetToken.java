package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
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
