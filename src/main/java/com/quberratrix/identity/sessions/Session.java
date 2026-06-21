package com.quberratrix.identity.sessions;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_sessions")
public class Session extends PersistableEntity {
    @Id
    private UUID id;
    private UUID userId;
    private UUID clientId;
    private UUID refreshTokenFamilyId;
    private String ipAddress;
    private String userAgent;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String location;
    private String status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private Instant revokedAt;
}
