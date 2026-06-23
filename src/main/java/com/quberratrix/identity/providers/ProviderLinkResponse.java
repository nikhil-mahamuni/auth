package com.quberratrix.identity.providers;

import java.time.Instant;
import java.util.UUID;

public record ProviderLinkResponse(
        UUID id,
        UUID userId,
        UUID providerId,
        String providerUserId,
        String providerEmail,
        String providerUsername,
        Instant linkedAt,
        Instant lastLoginAt
) {}
