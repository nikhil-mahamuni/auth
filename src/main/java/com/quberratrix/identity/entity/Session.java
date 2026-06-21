package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("identity_sessions")
public class Session extends PersistableEntity {
    @Id
    private UUID id;
    private UUID userId;
    private UUID clientId;
    private String ipAddress;
    private String userAgent;
    private String status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastAccessedAt;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String location;
    private UUID refreshTokenFamilyId;
    private Instant revokedAt;
}
