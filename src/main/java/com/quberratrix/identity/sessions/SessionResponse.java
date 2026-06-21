package com.quberratrix.identity.sessions;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID userId,
        UUID clientId,
        String ipAddress,
        String userAgent,
        String deviceId,
        String deviceName,
        String deviceType,
        String location,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt
) {}
