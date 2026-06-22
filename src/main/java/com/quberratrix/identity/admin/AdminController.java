package com.quberratrix.identity.admin;

import com.quberratrix.identity.audit.AuditService;
import com.quberratrix.identity.auth.AuthService;
import com.quberratrix.identity.clients.Client;
import com.quberratrix.identity.clients.ClientMapper;
import com.quberratrix.identity.clients.ClientRepository;
import com.quberratrix.identity.clients.ClientResponse;
import com.quberratrix.identity.common.ApiResponse;
import com.quberratrix.identity.common.CorrelationIdWebFilter;
import com.quberratrix.identity.common.IdentityException;
import com.quberratrix.identity.roles.RoleRepository;
import com.quberratrix.identity.roles.UserRoleRepository;
import com.quberratrix.identity.sessions.SessionRepository;
import com.quberratrix.identity.sessions.SessionResponse;
import com.quberratrix.identity.sessions.SessionMapper;
import com.quberratrix.identity.users.User;
import com.quberratrix.identity.users.UserMapper;
import com.quberratrix.identity.users.UserRepository;
import com.quberratrix.identity.users.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final SessionRepository sessionRepository;
    private final ClientRepository clientRepository;
    private final AuthService authService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    private Mono<UUID> getActorId() {
        return org.springframework.security.core.context.ReactiveSecurityContextHolder.getContext()
                .map(org.springframework.security.core.context.SecurityContext::getAuthentication)
                .map(org.springframework.security.core.Authentication::getPrincipal)
                .map(principal -> UUID.fromString(principal.toString()))
                .defaultIfEmpty(UUID.randomUUID());
    }

    private String getIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUa(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }

    private String getCorrelationId(ServerWebExchange exchange) {
        Object correlationId = exchange.getAttributes().get(CorrelationIdWebFilter.CORRELATION_ID_KEY);
        return correlationId != null ? correlationId.toString() : "";
    }

    private String getRequestId(ServerWebExchange exchange) {
        Object requestId = exchange.getAttributes().get(CorrelationIdWebFilter.REQUEST_ID_KEY);
        return requestId != null ? requestId.toString() : "";
    }

    @GetMapping("/users")
    public Flux<UserResponse> listUsers() {
        return userRepository.findAll().map(UserMapper::toResponse);
    }

    @GetMapping("/users/{id}")
    public Mono<ApiResponse<UserResponse>> getUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(UserMapper::toResponse)
                .map(ApiResponse::success)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)));
    }

    @PostMapping("/users")
    public Mono<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> userRepository.findByEmail(request.email())
                .flatMap(existing -> Mono.<User>error(new IdentityException("Email exists", "EMAIL_EXISTS", HttpStatus.CONFLICT)))
                .switchIfEmpty(Mono.defer(() -> Mono.fromCallable(() -> passwordEncoder.encode(request.password()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(hash -> {
                            User u = new User();
                            u.setId(UUID.randomUUID());
                            u.setEmail(request.email());
                            u.setPasswordHash(hash);
                            u.setFirstName(request.firstName());
                            u.setLastName(request.lastName());
                            u.setDisplayName(request.displayName());
                            u.setUserType(request.userType());
                            u.setStatus("ACTIVE");
                            u.setCreatedAt(Instant.now());
                            u.setUpdatedAt(Instant.now());
                            u.setPasswordUpdatedAt(Instant.now());
                            return userRepository.save(u);
                        })
                        .flatMap(user -> auditService.logAndPublishEvent("ADMIN_USER_CREATED", actorId, user.getId(), null, null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of("email", user.getEmail()))
                            .thenReturn(user))
                )).map(UserMapper::toResponse).map(ApiResponse::success));
    }

    @PostMapping("/users/{id}/enable")
    public Mono<ApiResponse<Void>> enableUser(@PathVariable UUID id, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    user.setStatus("ACTIVE");
                    user.setNotNew();
                    return userRepository.save(user)
                            .flatMap(u -> auditService.logAndPublishEvent("USER_ENABLED", actorId, u.getId(), null, null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of()));
                }).thenReturn(ApiResponse.success((Void) null, "User enabled.")));
    }

    @PostMapping("/users/{id}/disable")
    public Mono<ApiResponse<Void>> disableUser(@PathVariable UUID id, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    user.setStatus("DISABLED");
                    user.setNotNew();
                    return userRepository.save(user)
                            .flatMap(u -> auditService.logAndPublishEvent("USER_DISABLED", actorId, u.getId(), null, null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of()));
                }).thenReturn(ApiResponse.success((Void) null, "User disabled.")));
    }

    @PostMapping("/users/{id}/lock")
    public Mono<ApiResponse<Void>> lockUser(@PathVariable UUID id, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    user.setStatus("LOCKED");
                    user.setNotNew();
                    return userRepository.save(user)
                            .flatMap(u -> auditService.logAndPublishEvent("USER_LOCKED", actorId, u.getId(), null, null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of("reason", "admin_action")));
                })
                .thenReturn(ApiResponse.success((Void) null, "User locked successfully.")));
    }

    @PostMapping("/users/{id}/unlock")
    public Mono<ApiResponse<Void>> unlockUser(@PathVariable UUID id, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    user.setStatus("ACTIVE");
                    user.setLockedUntil(null);
                    user.setFailedLoginAttempts(0);
                    user.setNotNew();
                    return userRepository.save(user)
                            .flatMap(u -> auditService.logAndPublishEvent("USER_UNLOCKED", actorId, u.getId(), null, null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of()));
                })
                .thenReturn(ApiResponse.success((Void) null, "User unlocked successfully.")));
    }

    @PostMapping("/users/{id}/roles/{roleName}")
    public Mono<ApiResponse<Void>> assignRole(@PathVariable UUID id, @PathVariable String roleName, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> roleRepository.findByName(roleName)
                .switchIfEmpty(Mono.error(new IdentityException("Role not found", "ROLE_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(role -> userRoleRepository.assignRole(id, role.getId())
                        .then(auditService.logAndPublishEvent("ROLE_ASSIGNED", actorId, id, null, null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of("role", roleName))))
                .thenReturn(ApiResponse.success((Void) null, "Role assigned.")));
    }

    @DeleteMapping("/users/{id}/roles/{roleName}")
    public Mono<ApiResponse<Void>> removeRole(@PathVariable UUID id, @PathVariable String roleName, ServerWebExchange exchange) {
         return getActorId().flatMap(actorId -> roleRepository.findByName(roleName)
                .switchIfEmpty(Mono.error(new IdentityException("Role not found", "ROLE_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(role -> userRoleRepository.deleteRoleByUserIdAndRoleId(id, role.getId())
                        .then(auditService.logAndPublishEvent("ROLE_REMOVED", actorId, id, null, null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of("role", roleName))))
                .thenReturn(ApiResponse.success((Void) null, "Role removed.")));
    }

    @DeleteMapping("/users/{id}/roles")
    public Mono<ApiResponse<Void>> removeRoles(@PathVariable UUID id) {
        return userRoleRepository.deleteRolesByUserId(id)
                .thenReturn(ApiResponse.success(null, "Roles removed."));
    }

    @GetMapping("/users/{id}/sessions")
    public Flux<SessionResponse> getUserSessions(@PathVariable UUID id) {
        return sessionRepository.findByUserIdAndStatus(id, "ACTIVE").map(SessionMapper::toResponse);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ApiResponse<Void>> revokeSession(@PathVariable UUID sessionId, ServerWebExchange exchange) {
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(new IdentityException("Session not found", "SESSION_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(session -> authService.revokeSessionExplicitly(session.getId(), getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange)))
                .thenReturn(ApiResponse.success(null, "Session revoked."));
    }

    @DeleteMapping("/users/{id}/sessions")
    public Mono<ApiResponse<Void>> revokeAllUserSessions(@PathVariable UUID id, ServerWebExchange exchange) {
        return authService.logoutAllSessions(id, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange))
                .thenReturn(ApiResponse.success(null, "All sessions revoked."));
    }

    @GetMapping("/clients")
    public Flux<ClientResponse> listClients() {
        return clientRepository.findAll().map(ClientMapper::toResponse);
    }

    @PostMapping("/clients")
    public Mono<ApiResponse<ClientResponse>> createClient(@Valid @RequestBody ClientRequest request, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> {
            Mono<String> secretMono = Mono.justOrEmpty(request.clientSecret());

            if ("CONFIDENTIAL".equals(request.clientType()) || "SERVICE".equals(request.clientType())) {
                if (request.clientSecret() == null || request.clientSecret().isBlank()) {
                    return Mono.error(new IdentityException("Secret required for confidential clients", "SECRET_REQUIRED", HttpStatus.BAD_REQUEST));
                }
                secretMono = Mono.fromCallable(() -> passwordEncoder.encode(request.clientSecret()))
                        .subscribeOn(Schedulers.boundedElastic());
            }

            return secretMono
                    .defaultIfEmpty("")
                    .flatMap(secretHash -> {
                        Client c = new Client();
                        c.setId(UUID.randomUUID());
                        c.setClientId(request.clientId());
                        c.setClientName(request.clientName());
                        c.setClientType(request.clientType());
                        c.setClientSecretHash(secretHash.isEmpty() ? null : secretHash);
                        c.setEnabled(true);
                        c.setAllowedRedirectUrls(request.allowedRedirectUrls());
                        c.setAllowedWebOrigins(request.allowedWebOrigins());
                        c.setAccessTokenTtlSeconds(request.accessTokenTtlSeconds());
                        c.setRefreshTokenTtlSeconds(request.refreshTokenTtlSeconds());
                        c.setCreatedAt(Instant.now());
                        c.setUpdatedAt(Instant.now());
                        return clientRepository.save(c);
                    })
                    .flatMap(savedClient -> auditService.logAndPublishEvent("CLIENT_CREATED", actorId, null, savedClient.getId(), null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of("client_type", savedClient.getClientType()))
                            .thenReturn(savedClient))
                    .map(savedClient -> {
                        savedClient.setClientSecretHash(null);
                        return ApiResponse.success(ClientMapper.toResponse(savedClient));
                    });
        });
    }

    @PostMapping("/clients/{id}/enable")
    public Mono<ApiResponse<Void>> enableClient(@PathVariable UUID id, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> clientRepository.findById(id)
                .flatMap(c -> {
                    c.setEnabled(true);
                    c.setNotNew();
                    return clientRepository.save(c)
                            .flatMap(s -> auditService.logAndPublishEvent("CLIENT_ENABLED", actorId, null, c.getId(), null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of()));
                }).thenReturn(ApiResponse.success((Void) null, "Client enabled.")));
    }

    @PostMapping("/clients/{id}/disable")
    public Mono<ApiResponse<Void>> disableClient(@PathVariable UUID id, ServerWebExchange exchange) {
        return getActorId().flatMap(actorId -> clientRepository.findById(id)
                .flatMap(c -> {
                    c.setEnabled(false);
                    c.setNotNew();
                    return clientRepository.save(c)
                            .flatMap(s -> auditService.logAndPublishEvent("CLIENT_DISABLED", actorId, null, c.getId(), null, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange), Map.of()));
                }).thenReturn(ApiResponse.success((Void) null, "Client disabled.")));
    }
}
