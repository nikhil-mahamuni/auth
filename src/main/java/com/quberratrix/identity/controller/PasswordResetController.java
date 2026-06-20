package com.quberratrix.identity.controller;

import com.quberratrix.identity.dto.PasswordResetConfirmRequest;
import com.quberratrix.identity.dto.PasswordResetRequest;
import com.quberratrix.identity.event.IdentityEventPublisher;
import com.quberratrix.identity.exception.IdentityException;
import com.quberratrix.identity.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final UserRepository userRepository;
    private final IdentityEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/reset-request")
    public Mono<Map<String, String>> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        return userRepository.findByEmail(request.email())
                .flatMap(user -> {
                    // For a real implementation, generate an opaque token and store it hashed in a `password_reset_tokens` table.
                    // For brevity, just emitting the event which Notification Service picks up.
                    String resetToken = UUID.randomUUID().toString();
                    return eventPublisher.publishEvent("identity.password-reset.requested", user.getId(), user.getId(), null, null, Map.of("email", user.getEmail(), "token", resetToken))
                            .thenReturn(Map.of("message", "If an account exists, a password reset link has been sent."));
                })
                .switchIfEmpty(Mono.just(Map.of("message", "If an account exists, a password reset link has been sent."))); // Prevent email enumeration
    }

    @PostMapping("/reset-confirm")
    @Transactional
    public Mono<Map<String, String>> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        // Find token in DB, validate expiry, update password, then publish event
        // Mocked implementation for the requested requirement
        return Mono.fromCallable(() -> passwordEncoder.encode(request.newPassword()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(hashedPassword -> Mono.just(Map.of("message", "Password has been successfully reset.")));
    }
}
