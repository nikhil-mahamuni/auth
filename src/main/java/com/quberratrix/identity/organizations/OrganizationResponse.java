package com.quberratrix.identity.organizations;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
