package com.quberratrix.identity.organizations;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberResponse(
        UUID id,
        UUID organizationId,
        UUID userId,
        String role,
        String status,
        Instant joinedAt
) {}
