package com.quberratrix.identity.clients;

public class ClientMapper {
    public static ClientResponse toResponse(Client client) {
        if (client == null) return null;
        return new ClientResponse(
                client.getId(),
                client.getClientId(),
                client.getClientName(),
                client.getClientType(),
                client.isEnabled(),
                client.getAllowedRedirectUrls(),
                client.getAllowedWebOrigins(),
                client.getTokenEndpointAuthMethod(),
                client.getAccessTokenTtlSeconds(),
                client.getRefreshTokenTtlSeconds(),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}
