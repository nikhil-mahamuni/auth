package com.quberratrix.identity.entity;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Data
@Table("user_roles")
public class UserRole {
    private UUID userId;
    private UUID roleId;
}
