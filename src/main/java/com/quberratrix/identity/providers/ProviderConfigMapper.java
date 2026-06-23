package com.quberratrix.identity.providers;

public class ProviderConfigMapper {
    public static ProviderConfigResponse toResponse(ProviderConfig config) {
        if (config == null) return null;
        return new ProviderConfigResponse(
                config.getId(),
                config.getProviderKey(),
                config.getProviderName(),
                config.getProviderType(),
                config.getIssuerUri(),
                config.getAuthorizationUri(),
                config.getTokenUri(),
                config.getUserInfoUri(),
                config.getJwksUri(),
                config.getScopes(),
                config.isEnabled(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
