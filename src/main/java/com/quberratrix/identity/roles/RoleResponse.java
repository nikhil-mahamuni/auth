package com.quberratrix.identity.roles;

import java.time.Instant;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        Instant createdAt
) {}
