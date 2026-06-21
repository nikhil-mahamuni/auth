package com.quberratrix.identity.repository;

import com.quberratrix.identity.entity.Role;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RoleRepository extends ReactiveCrudRepository<Role, UUID> {
    Mono<Role> findByName(String name);

    @Query("SELECT r.* FROM identity_roles r INNER JOIN identity_user_roles ur ON r.id = ur.role_id WHERE ur.user_id = :userId")
    Flux<Role> findByUserId(UUID userId);
}
