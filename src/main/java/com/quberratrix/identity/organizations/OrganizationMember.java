package com.quberratrix.identity.organizations;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_organization_members")
public class OrganizationMember extends PersistableEntity {
    @Id
    private UUID id;
    private UUID organizationId;
    private UUID userId;
    private String role;
    private String status;
    private Instant joinedAt;
}
