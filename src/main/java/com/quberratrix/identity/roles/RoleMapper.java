package com.quberratrix.identity.roles;

public class RoleMapper {
    public static RoleResponse toResponse(Role role) {
        if (role == null) return null;
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedAt()
        );
    }
}
