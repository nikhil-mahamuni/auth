package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("identity_clients")
public class Client extends PersistableEntity {
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
