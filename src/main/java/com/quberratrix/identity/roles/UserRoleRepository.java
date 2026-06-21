package com.quberratrix.identity.roles;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserRoleRepository extends ReactiveCrudRepository<UserRole, UUID> {

    @Modifying
    @Query("INSERT INTO identity_user_roles (id, user_id, role_id) VALUES (gen_random_uuid(), :userId, :roleId)")
    Mono<Void> assignRole(UUID userId, UUID roleId);

    @Modifying
    @Query("DELETE FROM identity_user_roles WHERE user_id = :userId")
    Mono<Void> deleteRolesByUserId(UUID userId);
}
