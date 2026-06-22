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
@Table("identity_organizations")
public class Organization extends PersistableEntity {
    @Id
    private UUID id;
    private String name;
    private String slug;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
