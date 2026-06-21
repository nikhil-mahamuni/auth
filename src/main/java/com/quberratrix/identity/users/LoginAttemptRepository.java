package com.quberratrix.identity.users;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface LoginAttemptRepository extends ReactiveCrudRepository<LoginAttempt, UUID> {
}
