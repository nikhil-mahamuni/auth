package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("refresh_tokens")
public class RefreshToken {
    @Id
    private UUID id;
    private String tokenHash;
    private UUID sessionId;
    private UUID userId;
    private UUID clientId;
    private Instant issuedAt;
    private Instant expiresAt;
    private boolean revoked;
}
