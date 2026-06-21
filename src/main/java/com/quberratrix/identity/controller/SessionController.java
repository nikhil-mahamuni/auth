package com.quberratrix.identity.controller;

import com.quberratrix.identity.entity.Session;
import com.quberratrix.identity.repository.SessionRepository;
import com.quberratrix.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionRepository sessionRepository;
    private final AuthService authService;

    private String getIp(ServerHttpRequest request) {
        return request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUa(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }

    private Mono<UUID> getCurrentUserId() {
        // Based on our stateless JWT configuration, Spring Security populates the principal
        // We assume the principal is the subject (User ID) as a String.
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .map(principal -> UUID.fromString(principal.toString()));
    }

    @GetMapping
    public Flux<Session> getUserSessions() {
        return getCurrentUserId()
                .flatMapMany(userId -> sessionRepository.findByUserIdAndStatus(userId, "ACTIVE"));
    }

    @DeleteMapping("/all")
    public Mono<Void> revokeAllSessions(ServerHttpRequest request) {
        return getCurrentUserId()
                .flatMap(userId -> authService.logoutAllSessions(userId, getIp(request), getUa(request)));
    }
}
