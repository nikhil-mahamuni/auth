package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Data
@Table("identity_user_roles")
public class UserRole {
    @Id
    private UUID id;
    private UUID userId;
    private UUID roleId;
}
