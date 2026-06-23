package com.quberratrix.identity.providers;

public class ProviderLinkMapper {
    public static ProviderLinkResponse toResponse(ProviderLink link) {
        if (link == null) return null;
        return new ProviderLinkResponse(
                link.getId(),
                link.getUserId(),
                link.getProviderId(),
                link.getProviderUserId(),
                link.getProviderEmail(),
                link.getProviderUsername(),
                link.getLinkedAt(),
                link.getLastLoginAt()
        );
    }
}
