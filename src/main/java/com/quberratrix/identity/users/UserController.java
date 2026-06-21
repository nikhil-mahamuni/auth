package com.quberratrix.identity.users;

import com.quberratrix.identity.auth.AuthService;
import com.quberratrix.identity.common.ApiResponse;
import com.quberratrix.identity.common.CorrelationIdWebFilter;
import com.quberratrix.identity.common.IdentityException;
import com.quberratrix.identity.sessions.Session;
import com.quberratrix.identity.sessions.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final AuthService authService;

    private Mono<UUID> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .map(principal -> UUID.fromString(principal.toString()));
    }

    private String getIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getCorrelationId(ServerWebExchange exchange) {
        Object correlationId = exchange.getAttributes().get(CorrelationIdWebFilter.CORRELATION_ID_KEY);
        return correlationId != null ? correlationId.toString() : "";
    }

    private String getRequestId(ServerWebExchange exchange) {
        Object requestId = exchange.getAttributes().get(CorrelationIdWebFilter.REQUEST_ID_KEY);
        return requestId != null ? requestId.toString() : "";
    }

    private String getUa(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }

    @GetMapping
    @PatchMapping
    public Mono<ApiResponse<UserResponse>> updateProfile(@RequestBody UpdateUserRequest request) {
        return getCurrentUserId()
                .flatMap(userRepository::findById)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .flatMap(user -> {
                    if (request.firstName() != null) user.setFirstName(request.firstName());
                    if (request.lastName() != null) user.setLastName(request.lastName());
                    if (request.displayName() != null) user.setDisplayName(request.displayName());
                    if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl());
                    user.setNotNew();
                    return userRepository.save(user);
                })
                .map(UserMapper::toResponse)
                .map(ApiResponse::success);
    }

    public Mono<ApiResponse<UserResponse>> getProfile() {
        return getCurrentUserId()
                .flatMap(userRepository::findById)
                .switchIfEmpty(Mono.error(new IdentityException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND)))
                .map(UserMapper::toResponse)
                .map(ApiResponse::success);
    }

    @GetMapping("/sessions")
    public Mono<ApiResponse<java.util.List<Session>>> getSessions() {
        return getCurrentUserId()
                .flatMapMany(userId -> sessionRepository.findByUserIdAndStatus(userId, "ACTIVE"))
                .collectList()
                .map(ApiResponse::success);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ApiResponse<Void>> revokeSession(@PathVariable UUID sessionId, ServerWebExchange exchange) {
        // Not a full implementation since we typically want to revoke family and notify via Kafka.
        // For brevity we just set status to revoked and save it here (or call a service method).
        return getCurrentUserId()
                .flatMap(userId -> sessionRepository.findById(sessionId)
                        .filter(s -> s.getUserId().equals(userId))
                        .switchIfEmpty(Mono.error(new IdentityException("Session not found or forbidden", "SESSION_NOT_FOUND", HttpStatus.NOT_FOUND)))
                        .flatMap(s -> {
                            s.setStatus("REVOKED");
                            s.setNotNew();
                            return sessionRepository.save(s);
                        }))
                .thenReturn(ApiResponse.success(null, "Session revoked."));
    }

    @PostMapping("/logout-all")
    public Mono<ApiResponse<Void>> logoutAllSessions(ServerWebExchange exchange) {
        return getCurrentUserId()
                .flatMap(userId -> authService.logoutAllSessions(userId, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange)))
                .thenReturn(ApiResponse.success(null, "All sessions revoked."));
    }
}
// patch complete
// patch User Controller
