package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("sessions")
public class Session {
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
}
