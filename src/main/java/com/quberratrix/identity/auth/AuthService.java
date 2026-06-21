package com.quberratrix.identity.auth;

import com.quberratrix.identity.audit.AuditService;
import com.quberratrix.identity.clients.Client;
import com.quberratrix.identity.clients.ClientRepository;
import com.quberratrix.identity.common.IdentityException;
import com.quberratrix.identity.config.properties.AuthProperties;
import com.quberratrix.identity.jwks.JwtService;
import com.quberratrix.identity.roles.Role;
import com.quberratrix.identity.roles.RoleRepository;
import com.quberratrix.identity.roles.UserRoleRepository;
import com.quberratrix.identity.sessions.Session;
import com.quberratrix.identity.sessions.SessionRepository;
import com.quberratrix.identity.tokens.RefreshToken;
import com.quberratrix.identity.tokens.RefreshTokenRepository;
import com.quberratrix.identity.tokens.RevokedAccessToken;
import com.quberratrix.identity.tokens.RevokedAccessTokenRepository;
import com.quberratrix.identity.tokens.TokenServices;
import com.quberratrix.identity.users.LoginAttempt;
import com.quberratrix.identity.users.LoginAttemptRepository;
import com.quberratrix.identity.users.User;
import com.quberratrix.identity.users.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final ClientRepository clientRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AuthProperties authProperties;
    private final TokenServices tokenServices;

    private Mono<String> encodePassword(String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoder.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> matchesPassword(String rawPassword, String encodedPassword) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, encodedPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<User> register(RegisterRequest request, String ipAddress, String userAgent, String correlationId, String requestId) {
        return userRepository.findByEmail(request.email())
                .flatMap(existing -> Mono.<User>error(new IdentityException("Email already in use", "EMAIL_EXISTS", HttpStatus.CONFLICT)))
                .switchIfEmpty(Mono.defer(() -> encodePassword(request.password())
                        .flatMap(hashedPassword -> {
                            User newUser = new User();
                            newUser.setId(UUID.randomUUID());
                            newUser.setEmail(request.email());
                            newUser.setPasswordHash(hashedPassword);
                            newUser.setFirstName(request.firstName());
                            newUser.setLastName(request.lastName());
                            newUser.setDisplayName(request.displayName());
                            newUser.setUserType("PUBLIC_USER");
                            newUser.setStatus("ACTIVE");
                            newUser.setEmailVerified(false);
                            newUser.setCreatedAt(Instant.now());
                            newUser.setUpdatedAt(Instant.now());
                            newUser.setPasswordUpdatedAt(Instant.now());

                            return userRepository.save(newUser)
                                    .flatMap(savedUser -> roleRepository.findByName("ROLE_USER")
                                            .flatMap(role -> userRoleRepository.assignRole(savedUser.getId(), role.getId()))
                                            .thenReturn(savedUser)
                                    )
                                    .flatMap(savedUser -> auditService.logAndPublishEvent(
                                            "USER_REGISTERED",
                                            savedUser.getId(),
                                            savedUser.getId(),
                                            null, null, ipAddress, userAgent, correlationId, requestId,
                                            Map.of("email", savedUser.getEmail())
                                    ).thenReturn(savedUser))
                                    .flatMap(savedUser -> tokenServices.requestEmailVerification(savedUser.getEmail(), ipAddress, userAgent, correlationId, requestId)
                                            .thenReturn(savedUser)
                                    );
                        })));
    }

    @Transactional
    public Mono<LoginResult> login(AuthRequest request, String ipAddress, String userAgent, String deviceId, String deviceName, String deviceType, String location, String correlationId, String requestId) {
        return clientRepository.findByClientId(request.clientId() != null ? request.clientId() : "dev-client")
                .switchIfEmpty(Mono.error(new IdentityException("Invalid client", "INVALID_CLIENT", HttpStatus.BAD_REQUEST)))
                .flatMap(client -> {
                    if (!client.isEnabled() || "SERVICE".equals(client.getClientType())) {
                        return Mono.error(new IdentityException("Client is invalid or disabled", "CLIENT_DISABLED", HttpStatus.FORBIDDEN));
                    }
                    return userRepository.findByEmail(request.email())
                            .switchIfEmpty(logAttempt(null, request.email(), client.getId(), false, "user_not_found", ipAddress, userAgent, correlationId, requestId)
                                    .then(Mono.error(new IdentityException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED))))
                            .flatMap(user -> handleLoginAttempt(user, request.password(), client, ipAddress, userAgent, deviceId, deviceName, deviceType, location, correlationId, requestId));
                });
    }

    private Mono<LoginResult> handleLoginAttempt(User user, String rawPassword, Client client, String ipAddress, String userAgent, String deviceId, String deviceName, String deviceType, String location, String correlationId, String requestId) {
        if ("DISABLED".equals(user.getStatus()) || "DELETED".equals(user.getStatus()) || "LOCKED".equals(user.getStatus()) || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))) {
            return logAttempt(user.getId(), user.getEmail(), client.getId(), false, "account_locked_or_disabled", ipAddress, userAgent, correlationId, requestId)
                    .then(auditService.logAndPublishEvent("USER_LOGIN_FAILED_LOCKED", user.getId(), user.getId(), client.getId(), null, ipAddress, userAgent, correlationId, requestId, Map.of()))
                    .then(Mono.error(new IdentityException("Account is locked or disabled", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN)));
        }

        return matchesPassword(rawPassword, user.getPasswordHash())
                .flatMap(matches -> {
                    if (!matches) {
                        return handleFailedLogin(user, client, ipAddress, userAgent, correlationId, requestId);
                    }

                    user.setFailedLoginAttempts(0);
                    user.setLockedUntil(null);
                    user.setLastLogin(Instant.now());
                    user.setNotNew();

                    return userRepository.save(user)
                            .flatMap(savedUser -> logAttempt(user.getId(), user.getEmail(), client.getId(), true, null, ipAddress, userAgent, correlationId, requestId)
                                .then(createSessionAndTokens(savedUser, client, ipAddress, userAgent, deviceId, deviceName, deviceType, location, UUID.randomUUID(), correlationId, requestId)));
                });
    }

    private Mono<Void> logAttempt(UUID userId, String email, UUID clientId, boolean success, String reason, String ipAddress, String userAgent, String correlationId, String requestId) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setId(UUID.randomUUID());
        attempt.setUserId(userId);
        attempt.setEmail(email);
        attempt.setClientId(clientId);
        attempt.setIpAddress(ipAddress);
        attempt.setUserAgent(userAgent);
        attempt.setSuccess(success);
        attempt.setFailureReason(reason);
        attempt.setCorrelationId(correlationId);
        attempt.setRequestId(requestId);
        attempt.setCreatedAt(Instant.now());
        return loginAttemptRepository.save(attempt).then();
    }

    private Mono<LoginResult> handleFailedLogin(User user, Client client, String ipAddress, String userAgent, String correlationId, String requestId) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        user.setNotNew();
        if (user.getFailedLoginAttempts() >= authProperties.getLogin().getMaxFailedAttempts()) {
            user.setLockedUntil(Instant.now().plus(authProperties.getLogin().getLockDuration()));
            return userRepository.save(user)
                    .flatMap(savedUser -> logAttempt(user.getId(), user.getEmail(), client.getId(), false, "max_attempts_reached", ipAddress, userAgent, correlationId, requestId))
                    .then(auditService.logAndPublishEvent("USER_LOCKED", user.getId(), user.getId(), client.getId(), null, ipAddress, userAgent, correlationId, requestId, Map.of("reason", "max_attempts")))
                    .then(auditService.logAndPublishEvent("USER_LOGIN_FAILED", user.getId(), user.getId(), client.getId(), null, ipAddress, userAgent, correlationId, requestId, Map.of("reason", "bad_password")))
                    .then(Mono.error(new IdentityException("Account is locked due to too many failed attempts", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN)));
        }
        return userRepository.save(user)
                .flatMap(savedUser -> logAttempt(user.getId(), user.getEmail(), client.getId(), false, "bad_password", ipAddress, userAgent, correlationId, requestId))
                .then(auditService.logAndPublishEvent("USER_LOGIN_FAILED", user.getId(), user.getId(), client.getId(), null, ipAddress, userAgent, correlationId, requestId, Map.of("reason", "bad_password")))
                .then(Mono.error(new IdentityException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED)));
    }

    private String sha256(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }
    }

    @Transactional
    public Mono<LoginResult> refreshWithSha256(String rawRefreshToken, String clientId, String ipAddress, String userAgent, String deviceId, String deviceName, String deviceType, String location, String correlationId, String requestId) {
         if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Mono.error(new IdentityException("Missing refresh token", "MISSING_TOKEN", HttpStatus.UNAUTHORIZED));
        }

        String hashedToken = sha256(rawRefreshToken);

        return refreshTokenRepository.findByTokenHash(hashedToken)
                .switchIfEmpty(Mono.error(new IdentityException("Invalid refresh token", "INVALID_TOKEN", HttpStatus.UNAUTHORIZED)))
                .flatMap(refreshToken -> {
                    if (!"ACTIVE".equals(refreshToken.getStatus()) || refreshToken.getExpiresAt().isBefore(Instant.now())) {

                        // Reuse detection logic: any status other than ACTIVE means it was already used or revoked
                        if ("ROTATED".equals(refreshToken.getStatus()) || "REUSED".equals(refreshToken.getStatus()) || "REVOKED".equals(refreshToken.getStatus())) {
                            refreshToken.setStatus("REUSED");
                            refreshToken.setNotNew();
                            return refreshTokenRepository.save(refreshToken)
                                    .flatMap(r -> revokeFamily(refreshToken.getFamilyId(), refreshToken.getSessionId(), "token_reuse_detected"))
                                    .flatMap(v -> auditService.logAndPublishEvent("TOKEN_REUSED", refreshToken.getUserId(), refreshToken.getUserId(), refreshToken.getClientId(), refreshToken.getSessionId(), ipAddress, userAgent, correlationId, requestId, Map.of("familyId", refreshToken.getFamilyId().toString())))
                                    .then(Mono.error(new IdentityException("Token reuse detected. Session revoked.", "TOKEN_REUSE_DETECTED", HttpStatus.UNAUTHORIZED)));
                        }

                        return Mono.error(new IdentityException("Token expired or invalid", "TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED));
                    }

                    return clientRepository.findById(refreshToken.getClientId())
                            .flatMap(client -> userRepository.findById(refreshToken.getUserId())
                                    .flatMap(user -> {
                                         if ("DISABLED".equals(user.getStatus()) || "DELETED".equals(user.getStatus()) || "LOCKED".equals(user.getStatus()) || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) || !client.isEnabled()) {
                                              return Mono.error(new IdentityException("Account locked or client disabled", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN));
                                         }

                                         // Rotate the token
                                         refreshToken.setStatus("ROTATED");
                                         refreshToken.setRotatedAt(Instant.now());
                                         refreshToken.setNotNew();
                                         return refreshTokenRepository.save(refreshToken)
                                                 .then(rotateTokensAndUpdateSession(user, client, ipAddress, userAgent, refreshToken.getSessionId(), refreshToken.getFamilyId(), correlationId, requestId));
                                    }));
                });
    }

    private Mono<LoginResult> rotateTokensAndUpdateSession(User user, Client client, String ipAddress, String userAgent, UUID sessionId, UUID familyId, String correlationId, String requestId) {
        return sessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setLastUsedAt(Instant.now());
                    session.setNotNew();
                    return sessionRepository.save(session);
                })
                .flatMap(session -> roleRepository.findByUserId(user.getId()).collectList()
                        .flatMap(roles -> {
                            List<String> roleNames = roles.stream().map(Role::getName).toList();
                            String accessToken = jwtService.generateAccessToken(
                                    user.getId(), user.getEmail(), user.getUserType(), roleNames, client.getId(), session.getId()
                            );

                            String rawRefreshToken = generateOpaqueToken();
                            String hashedRefreshToken = sha256(rawRefreshToken);
                            long refreshTokenTtl = client.getRefreshTokenTtlSeconds() != null ? client.getRefreshTokenTtlSeconds() : 2592000L;

                            RefreshToken refreshToken = new RefreshToken();
                            refreshToken.setId(UUID.randomUUID());
                            refreshToken.setTokenHash(hashedRefreshToken);
                            refreshToken.setSessionId(session.getId());
                            refreshToken.setUserId(user.getId());
                            refreshToken.setClientId(client.getId());
                            refreshToken.setIssuedAt(Instant.now());
                            refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenTtl));
                            refreshToken.setFamilyId(familyId);
                            refreshToken.setStatus("ACTIVE");

                            return refreshTokenRepository.save(refreshToken)
                                    .flatMap(rt -> {
                                        AuthResponse authResponse = new AuthResponse(accessToken, "JWT", 900L);
                                        return auditService.logAndPublishEvent("TOKEN_REFRESHED", user.getId(), user.getId(), client.getId(), session.getId(), ipAddress, userAgent, correlationId, requestId, Map.of())
                                                .thenReturn(new LoginResult(authResponse, rawRefreshToken, refreshTokenTtl));
                                    });
                        }));
    }

    private Mono<Void> revokeFamily(UUID familyId, UUID sessionId, String reason) {
        return refreshTokenRepository.findByFamilyId(familyId)
                .flatMap(token -> {
                    if (!"REVOKED".equals(token.getStatus()) && !"REUSED".equals(token.getStatus())) {
                        token.setStatus("REVOKED");
                        token.setRevokedAt(Instant.now());
                        token.setRevokeReason(reason);
                        token.setNotNew();
                        return refreshTokenRepository.save(token);
                    }
                    return Mono.just(token);
                })
                .then(sessionRepository.findById(sessionId)
                        .flatMap(session -> {
                            session.setStatus("REVOKED");
                            session.setRevokedAt(Instant.now());
                            session.setNotNew();
                            return sessionRepository.save(session);
                        }))
                .then();
    }

    @Transactional
    public Mono<Void> logout(String rawRefreshToken, String accessToken, String ipAddress, String userAgent, String correlationId, String requestId) {
        Mono<Void> revokeAccessMono = Mono.empty();

        if (accessToken != null && !accessToken.isBlank()) {
            try {
                Claims claims = jwtService.validateToken(accessToken);
                String jti = claims.getId();
                if (jti != null) {
                    RevokedAccessToken revoked = new RevokedAccessToken();
                    revoked.setId(UUID.randomUUID());
                    revoked.setJti(UUID.fromString(jti));
                    revoked.setUserId(UUID.fromString(claims.getSubject()));
                    revoked.setRevokedAt(Instant.now());
                    revoked.setExpiresAt(claims.getExpiration().toInstant());
                    revokeAccessMono = revokedAccessTokenRepository.save(revoked).then();
                }
            } catch (Exception ignored) {
            }
        }

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return revokeAccessMono;
        }

        String hashedToken = sha256(rawRefreshToken);
        Mono<Void> revokeRefreshMono = refreshTokenRepository.findByTokenHash(hashedToken)
                .flatMap(token -> {
                    token.setStatus("REVOKED");
                    token.setRevokedAt(Instant.now());
                    token.setRevokeReason("user_logout");
                    token.setNotNew();
                    return refreshTokenRepository.save(token)
                            .flatMap(t -> revokeFamily(token.getFamilyId(), token.getSessionId(), "user_logout"))
                            .then(auditService.logAndPublishEvent("USER_LOGOUT", token.getUserId(), token.getUserId(), token.getClientId(), token.getSessionId(), ipAddress, userAgent, correlationId, requestId, Map.of()));
                }).then();

        return revokeAccessMono.then(revokeRefreshMono);
    }

    @Transactional
    public Mono<Void> logoutAllSessions(UUID userId, String ipAddress, String userAgent, String correlationId, String requestId) {
        return sessionRepository.findByUserIdAndStatus(userId, "ACTIVE")
                .flatMap(session -> {
                    session.setStatus("REVOKED");
                    session.setRevokedAt(Instant.now());
                    session.setNotNew();
                    return sessionRepository.save(session)
                            .flatMap(s -> revokeFamily(s.getRefreshTokenFamilyId(), s.getId(), "user_logout_all"));
                })
                .then(auditService.logAndPublishEvent("USER_LOGOUT_ALL", userId, userId, null, null, ipAddress, userAgent, correlationId, requestId, Map.of()))
                .then();
    }

    private Mono<LoginResult> createSessionAndTokens(User user, Client client, String ipAddress, String userAgent, String deviceId, String deviceName, String deviceType, String location, UUID familyId, String correlationId, String requestId) {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setClientId(client.getId());
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setDeviceId(deviceId);
        session.setDeviceName(deviceName);
        session.setDeviceType(deviceType);
        session.setLocation(location);
        session.setRefreshTokenFamilyId(familyId);
        session.setStatus("ACTIVE");
        session.setCreatedAt(Instant.now());
        session.setLastUsedAt(Instant.now());

        long refreshTokenTtl = client.getRefreshTokenTtlSeconds() != null ? client.getRefreshTokenTtlSeconds() : 2592000L;
        session.setExpiresAt(Instant.now().plusSeconds(refreshTokenTtl));

        return sessionRepository.save(session)
                .flatMap(savedSession -> roleRepository.findByUserId(user.getId()).collectList()
                        .flatMap(roles -> {
                            List<String> roleNames = roles.stream().map(Role::getName).toList();
                            String accessToken = jwtService.generateAccessToken(
                                    user.getId(), user.getEmail(), user.getUserType(), roleNames, client.getId(), savedSession.getId()
                            );

                            String rawRefreshToken = generateOpaqueToken();
                            String hashedRefreshToken = sha256(rawRefreshToken);

                            RefreshToken refreshToken = new RefreshToken();
                            refreshToken.setId(UUID.randomUUID());
                            refreshToken.setTokenHash(hashedRefreshToken);
                            refreshToken.setSessionId(savedSession.getId());
                            refreshToken.setUserId(user.getId());
                            refreshToken.setClientId(client.getId());
                            refreshToken.setIssuedAt(Instant.now());
                            refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshTokenTtl));
                            refreshToken.setFamilyId(familyId);
                            refreshToken.setStatus("ACTIVE");

                            return refreshTokenRepository.save(refreshToken)
                                    .flatMap(rt -> {
                                        AuthResponse authResponse = new AuthResponse(accessToken, "JWT", 900L);
                                        return auditService.logAndPublishEvent("USER_LOGIN", user.getId(), user.getId(), client.getId(), savedSession.getId(), ipAddress, userAgent, correlationId, requestId, Map.of())
                                                .thenReturn(new LoginResult(authResponse, rawRefreshToken, refreshTokenTtl));
                                    });
                        }));
    }

    private String generateOpaqueToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record LoginResult(AuthResponse response, String rawRefreshToken, long maxAge) {}
}
