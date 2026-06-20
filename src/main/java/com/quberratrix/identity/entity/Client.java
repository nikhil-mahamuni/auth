package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("clients")
public class Client {
    @Id
    private UUID id;
    private String clientId;
    private String clientName;
    private String clientType;
    private String clientSecretHash;
    private boolean enabled;
    private String allowedRedirectUrls;
    private String allowedWebOrigins;
    private Integer accessTokenTtl;
    private Integer refreshTokenTtl;
    private Instant createdAt;
    private Instant updatedAt;
}
