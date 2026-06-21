package com.quberratrix.identity.admin;

import jakarta.validation.constraints.NotBlank;

public record ClientRequest(
        @NotBlank String clientId,
        @NotBlank String clientName,
        @NotBlank String clientType,
        String allowedRedirectUrls,
        String allowedWebOrigins,
        Integer accessTokenTtlSeconds,
        Integer refreshTokenTtlSeconds
) {}
