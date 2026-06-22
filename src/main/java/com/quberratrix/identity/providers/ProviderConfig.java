package com.quberratrix.identity.providers;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_provider_configs")
public class ProviderConfig extends PersistableEntity {
    @Id
    private UUID id;
    private String providerKey;
    private String providerName;
    private String providerType;
    private String issuerUri;
    private String authorizationUri;
    private String tokenUri;
    private String userInfoUri;
    private String jwksUri;
    private String scopes;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
