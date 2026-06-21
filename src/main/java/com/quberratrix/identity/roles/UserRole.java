package com.quberratrix.identity.roles;

import com.quberratrix.identity.common.PersistableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Table("identity_user_roles")
public class UserRole extends PersistableEntity {
    @Id
    private UUID id;
    private UUID userId;
    private UUID roleId;
}
