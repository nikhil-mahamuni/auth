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
@Table("identity_provider_links")
public class ProviderLink extends PersistableEntity {
    @Id
    private UUID id;
    private UUID userId;
    private UUID providerId;
    private String providerUserId;
    private String providerEmail;
    private String providerUsername;
    private Instant linkedAt;
    private Instant lastLoginAt;
}
