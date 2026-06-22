package com.quberratrix.identity.clients;

import java.time.Instant;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String clientId,
        String clientName,
        String clientType,
        String status,
        String allowedRedirectUrls,
        String allowedWebOrigins,
        String tokenEndpointAuthMethod,
        Integer accessTokenTtlSeconds,
        Integer refreshTokenTtlSeconds,
        Instant createdAt,
        Instant updatedAt
) {}
