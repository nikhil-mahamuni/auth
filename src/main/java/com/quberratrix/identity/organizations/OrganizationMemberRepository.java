package com.quberratrix.identity.organizations;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface OrganizationMemberRepository extends ReactiveCrudRepository<OrganizationMember, UUID> {
    Flux<OrganizationMember> findByOrganizationId(UUID organizationId);
    Flux<OrganizationMember> findByUserId(UUID userId);
}
