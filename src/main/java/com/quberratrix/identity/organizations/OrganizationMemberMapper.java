package com.quberratrix.identity.organizations;

public class OrganizationMemberMapper {
    public static OrganizationMemberResponse toResponse(OrganizationMember member) {
        if (member == null) return null;
        return new OrganizationMemberResponse(
                member.getId(),
                member.getOrganizationId(),
                member.getUserId(),
                member.getRole(),
                member.getStatus(),
                member.getJoinedAt()
        );
    }
}
