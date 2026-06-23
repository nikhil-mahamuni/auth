package com.quberratrix.identity.providers;

import java.time.Instant;
import java.util.UUID;

public record ProviderConfigResponse(
        UUID id,
        String providerKey,
        String providerName,
        String providerType,
        String issuerUri,
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        String jwksUri,
        String scopes,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
