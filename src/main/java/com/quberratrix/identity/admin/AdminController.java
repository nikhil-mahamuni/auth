package com.quberratrix.identity.admin;

import com.quberratrix.identity.auth.AuthService;
import com.quberratrix.identity.clients.Client;
import com.quberratrix.identity.clients.ClientRepository;
import com.quberratrix.identity.common.ApiResponse;
import com.quberratrix.identity.common.CorrelationIdWebFilter;
import com.quberratrix.identity.common.IdentityException;
import com.quberratrix.identity.roles.RoleRepository;
import com.quberratrix.identity.roles.UserRoleRepository;
import com.quberratrix.identity.sessions.Session;
import com.quberratrix.identity.sessions.SessionRepository;
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
    private final PasswordEncoder passwordEncoder;

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
    public Mono<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        return userRepository.findByEmail(request.email())
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
                )).map(UserMapper::toResponse).map(ApiResponse::success);
    }

    @PostMapping("/users/{id}/enable")
    public Mono<ApiResponse<Void>> enableUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    user.setStatus("ACTIVE");
                    user.setNotNew();
                    return userRepository.save(user);
                }).thenReturn(ApiResponse.success(null, "User enabled."));
    }

    @PostMapping("/users/{id}/disable")
    public Mono<ApiResponse<Void>> disableUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    user.setStatus("DISABLED");
                    user.setNotNew();
                    return userRepository.save(user);
                }).thenReturn(ApiResponse.success(null, "User disabled."));
    }

    @PostMapping("/users/{id}/lock")
    public Mono<ApiResponse<Void>> lockUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    user.setStatus("LOCKED");
                    user.setNotNew();
                    return userRepository.save(user);
                })
                .thenReturn(ApiResponse.success(null, "User locked successfully."));
    }

    @PostMapping("/users/{id}/unlock")
    public Mono<ApiResponse<Void>> unlockUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    user.setStatus("ACTIVE");
                    user.setLockedUntil(null);
                    user.setFailedLoginAttempts(0);
                    user.setNotNew();
                    return userRepository.save(user);
                })
                .thenReturn(ApiResponse.success(null, "User unlocked successfully."));
    }

    @PostMapping("/users/{id}/roles/{roleName}")
    public Mono<ApiResponse<Void>> assignRole(@PathVariable UUID id, @PathVariable String roleName) {
        return roleRepository.findByName(roleName)
                .switchIfEmpty(Mono.error(new IdentityException("Role not found", "ROLE_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(role -> userRoleRepository.assignRole(id, role.getId()))
                .thenReturn(ApiResponse.success(null, "Role assigned."));
    }

    @DeleteMapping("/users/{id}/roles")
    public Mono<ApiResponse<Void>> removeRoles(@PathVariable UUID id) {
        return userRoleRepository.deleteRolesByUserId(id)
                .thenReturn(ApiResponse.success(null, "Roles removed."));
    }

    @GetMapping("/users/{id}/sessions")
    public Flux<Session> getUserSessions(@PathVariable UUID id) {
        return sessionRepository.findByUserIdAndStatus(id, "ACTIVE");
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ApiResponse<Void>> revokeSession(@PathVariable UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(new IdentityException("Session not found", "SESSION_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(session -> {
                    session.setStatus("REVOKED");
                    session.setNotNew();
                    return sessionRepository.save(session);
                })
                .thenReturn(ApiResponse.success(null, "Session revoked."));
    }

    @DeleteMapping("/users/{id}/sessions")
    public Mono<ApiResponse<Void>> revokeAllUserSessions(@PathVariable UUID id, ServerWebExchange exchange) {
        return authService.logoutAllSessions(id, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange))
                .thenReturn(ApiResponse.success(null, "All sessions revoked."));
    }

    @GetMapping("/clients")
    public Flux<Client> listClients() {
        return clientRepository.findAll();
    }

    @PostMapping("/clients")
    public Mono<ApiResponse<Client>> createClient(@Valid @RequestBody ClientRequest request) {
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
                }).map(savedClient -> {
                    // Do not leak secret hash back
                    savedClient.setClientSecretHash(null);
                    return ApiResponse.success(savedClient);
                });
    }

    @PostMapping("/clients/{id}/enable")
    public Mono<ApiResponse<Void>> enableClient(@PathVariable UUID id) {
        return clientRepository.findById(id)
                .flatMap(c -> {
                    c.setEnabled(true);
                    c.setNotNew();
                    return clientRepository.save(c);
                }).thenReturn(ApiResponse.success(null, "Client enabled."));
    }

    @PostMapping("/clients/{id}/disable")
    public Mono<ApiResponse<Void>> disableClient(@PathVariable UUID id) {
        return clientRepository.findById(id)
                .flatMap(c -> {
                    c.setEnabled(false);
                    c.setNotNew();
                    return clientRepository.save(c);
                }).thenReturn(ApiResponse.success(null, "Client disabled."));
    }
}
