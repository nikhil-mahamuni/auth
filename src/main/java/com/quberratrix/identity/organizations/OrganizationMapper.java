package com.quberratrix.identity.organizations;

public class OrganizationMapper {
    public static OrganizationResponse toResponse(Organization organization) {
        if (organization == null) return null;
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getStatus(),
                organization.getCreatedAt(),
                organization.getUpdatedAt()
        );
    }
}
