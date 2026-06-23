package com.quberratrix.identity.tokens;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import com.quberratrix.identity.tokens.TokenStatus;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_email_verification_tokens")
public class EmailVerificationToken extends PersistableEntity {
    @Id
    private UUID id;
    private String tokenHash;
    private UUID userId;
    private Instant requestedAt;
    private Instant usedAt;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
    private Instant expiresAt;
    private String status;
}
