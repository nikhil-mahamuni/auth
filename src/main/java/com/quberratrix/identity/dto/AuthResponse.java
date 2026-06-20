package com.quberratrix.identity.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
        // Note: Refresh token will be returned as an HttpOnly cookie for web clients,
        // but could be included here if requested by specific clients like mobile.
) {}
