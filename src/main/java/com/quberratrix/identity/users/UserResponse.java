package com.quberratrix.identity.users;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String avatarUrl,
        String userType,
        String status,
        boolean emailVerified,
        Instant lastLogin,
        Instant createdAt
) {}
