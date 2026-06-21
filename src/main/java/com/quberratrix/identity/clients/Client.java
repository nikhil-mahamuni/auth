package com.quberratrix.identity.clients;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
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
    private String tokenEndpointAuthMethod;
    private Integer accessTokenTtlSeconds;
    private Integer refreshTokenTtlSeconds;
    private Instant createdAt;
    private Instant updatedAt;
}
