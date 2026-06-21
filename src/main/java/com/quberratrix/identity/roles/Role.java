package com.quberratrix.identity.roles;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_roles")
public class Role extends PersistableEntity {
    @Id
    private UUID id;
    private String name;
    private String description;
    private Instant createdAt;
}
