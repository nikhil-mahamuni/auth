package com.quberratrix.identity.service;

import com.quberratrix.identity.dto.AuthRequest;
import com.quberratrix.identity.dto.AuthResponse;
import com.quberratrix.identity.dto.RegisterRequest;
import com.quberratrix.identity.dto.ServiceTokenRequest;
import com.quberratrix.identity.entity.Client;
import com.quberratrix.identity.entity.RefreshToken;
import com.quberratrix.identity.entity.RevokedAccessToken;
import com.quberratrix.identity.entity.Role;
import com.quberratrix.identity.entity.Session;
import com.quberratrix.identity.entity.User;
import com.quberratrix.identity.exception.IdentityException;
import com.quberratrix.identity.jwt.JwtService;
import com.quberratrix.identity.repository.ClientRepository;
import com.quberratrix.identity.repository.RefreshTokenRepository;
import com.quberratrix.identity.repository.RevokedAccessTokenRepository;
import com.quberratrix.identity.repository.RoleRepository;
import com.quberratrix.identity.repository.SessionRepository;
import com.quberratrix.identity.repository.UserRepository;
import com.quberratrix.identity.repository.UserRoleRepository;
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
import java.time.temporal.ChronoUnit;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    private Mono<String> encodePassword(String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoder.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> matchesPassword(String rawPassword, String encodedPassword) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, encodedPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<User> register(RegisterRequest request, String ipAddress, String userAgent) {
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
                                            null, null, ipAddress, userAgent,
                                            Map.of("email", savedUser.getEmail())
                                    ).thenReturn(savedUser));
                        })));
    }

    @Transactional
    public Mono<LoginResult> login(AuthRequest request, String ipAddress, String userAgent, String deviceId, String deviceName, String deviceType, String location) {
        return clientRepository.findByClientId(request.clientId() != null ? request.clientId() : "dev-client")
                .switchIfEmpty(Mono.error(new IdentityException("Invalid client", "INVALID_CLIENT", HttpStatus.BAD_REQUEST)))
                .flatMap(client -> {
                    if (!client.isEnabled()) {
                        return Mono.error(new IdentityException("Client is disabled", "CLIENT_DISABLED", HttpStatus.FORBIDDEN));
                    }
                    return userRepository.findByEmail(request.email())
                            .switchIfEmpty(Mono.error(new IdentityException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED)))
                            .flatMap(user -> handleLoginAttempt(user, request.password(), client, ipAddress, userAgent, deviceId, deviceName, deviceType, location));
                });
    }

    private Mono<LoginResult> handleLoginAttempt(User user, String rawPassword, Client client, String ipAddress, String userAgent, String deviceId, String deviceName, String deviceType, String location) {
        if ("LOCKED".equals(user.getStatus()) || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))) {
            return auditService.logAndPublishEvent("USER_LOGIN_FAILED_LOCKED", user.getId(), user.getId(), client.getId(), null, ipAddress, userAgent, Map.of())
                    .then(Mono.error(new IdentityException("Account is locked", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN)));
        }

        return matchesPassword(rawPassword, user.getPasswordHash())
                .flatMap(matches -> {
                    if (!matches) {
                        return handleFailedLogin(user, client, ipAddress, userAgent);
                    }

                    user.setLoginAttempts(0);
                    user.setLockedUntil(null);
                    user.setLastLogin(Instant.now());
                    user.setNotNew();

                    return userRepository.save(user)
                            .flatMap(savedUser -> createSessionAndTokens(savedUser, client, ipAddress, userAgent, deviceId, deviceName, deviceType, location, UUID.randomUUID()));
                });
    }

    private Mono<LoginResult> handleFailedLogin(User user, Client client, String ipAddress, String userAgent) {
        user.setLoginAttempts(user.getLoginAttempts() + 1);
        user.setNotNew();
        if (user.getLoginAttempts() >= 5) {
            user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            return userRepository.save(user)
                    .flatMap(savedUser -> auditService.logAndPublishEvent("USER_LOCKED", user.getId(), user.getId(), client.getId(), null, ipAddress, userAgent, Map.of("reason", "max_attempts")))
                    .then(auditService.logAndPublishEvent("USER_LOGIN_FAILED", user.getId(), user.getId(), client.getId(), null, ipAddress, userAgent, Map.of("reason", "bad_password")))
                    .then(Mono.error(new IdentityException("Account is locked due to too many failed attempts", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN)));
        }
        return userRepository.save(user)
                .flatMap(savedUser -> auditService.logAndPublishEvent("USER_LOGIN_FAILED", user.getId(), user.getId(), client.getId(), null, ipAddress, userAgent, Map.of("reason", "bad_password")))
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
    public Mono<LoginResult> refreshWithSha256(String rawRefreshToken, String clientId, String ipAddress, String userAgent, String deviceId, String deviceName, String deviceType, String location) {
         if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Mono.error(new IdentityException("Missing refresh token", "MISSING_TOKEN", HttpStatus.UNAUTHORIZED));
        }

        String hashedToken = sha256(rawRefreshToken);

        return refreshTokenRepository.findByTokenHash(hashedToken)
                .switchIfEmpty(Mono.error(new IdentityException("Invalid refresh token", "INVALID_TOKEN", HttpStatus.UNAUTHORIZED)))
                .flatMap(refreshToken -> {
                    if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now()) || "REVOKED".equals(refreshToken.getStatus()) || "REUSED".equals(refreshToken.getStatus())) {

                        // Reuse detection logic
                        if ("ROTATED".equals(refreshToken.getStatus()) || refreshToken.isRevoked()) {
                            // The token was already used! Mark it reused and revoke family.
                            refreshToken.setStatus("REUSED");
                            refreshToken.setNotNew();
                            return refreshTokenRepository.save(refreshToken)
                                    .flatMap(r -> revokeFamily(refreshToken.getFamilyId(), refreshToken.getSessionId(), "token_reuse_detected"))
                                    .flatMap(v -> auditService.logAndPublishEvent("TOKEN_REUSED", refreshToken.getUserId(), refreshToken.getUserId(), refreshToken.getClientId(), refreshToken.getSessionId(), ipAddress, userAgent, Map.of("familyId", refreshToken.getFamilyId().toString())))
                                    .then(Mono.error(new IdentityException("Token reuse detected. Session revoked.", "TOKEN_REUSE_DETECTED", HttpStatus.UNAUTHORIZED)));
                        }

                        return Mono.error(new IdentityException("Token expired or revoked", "TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED));
                    }

                    return clientRepository.findById(refreshToken.getClientId())
                            .flatMap(client -> userRepository.findById(refreshToken.getUserId())
                                    .flatMap(user -> {
                                         if ("LOCKED".equals(user.getStatus()) || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) || !client.isEnabled()) {
                                              return Mono.error(new IdentityException("Account locked or client disabled", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN));
                                         }

                                         // Rotate the token
                                         refreshToken.setRevoked(true);
                                         refreshToken.setRotatedAt(Instant.now());
                                         refreshToken.setStatus("ROTATED");
                                         refreshToken.setNotNew();
                                         return refreshTokenRepository.save(refreshToken)
                                                 .then(createSessionAndTokens(user, client, ipAddress, userAgent, deviceId, deviceName, deviceType, location, refreshToken.getFamilyId()));
                                    }));
                });
    }

    private Mono<Void> revokeFamily(UUID familyId, UUID sessionId, String reason) {
        return refreshTokenRepository.findByFamilyId(familyId)
                .flatMap(token -> {
                    token.setRevoked(true);
                    token.setRevokedAt(Instant.now());
                    token.setStatus("REVOKED");
                    token.setRevokeReason(reason);
                    token.setNotNew();
                    return refreshTokenRepository.save(token);
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
    public Mono<Void> logout(String rawRefreshToken, String accessToken, String ipAddress, String userAgent) {
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
                // Ignore invalid/expired access token during logout
            }
        }

        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return revokeAccessMono;
        }

        String hashedToken = sha256(rawRefreshToken);
        Mono<Void> revokeRefreshMono = refreshTokenRepository.findByTokenHash(hashedToken)
                .flatMap(token -> {
                    token.setRevoked(true);
                    token.setRevokedAt(Instant.now());
                    token.setStatus("REVOKED");
                    token.setRevokeReason("user_logout");
                    token.setNotNew();
                    return refreshTokenRepository.save(token)
                            .flatMap(t -> sessionRepository.findById(t.getSessionId()))
                            .flatMap(session -> {
                                session.setStatus("REVOKED");
                                session.setRevokedAt(Instant.now());
                                session.setNotNew();
                                return sessionRepository.save(session);
                            })
                            .flatMap(s -> auditService.logAndPublishEvent("USER_LOGOUT", token.getUserId(), token.getUserId(), token.getClientId(), token.getSessionId(), ipAddress, userAgent, Map.of()));
                }).then();

        return revokeAccessMono.then(revokeRefreshMono);
    }

    @Transactional
    public Mono<Void> logoutAllSessions(UUID userId, String ipAddress, String userAgent) {
        return sessionRepository.findByUserIdAndStatus(userId, "ACTIVE")
                .flatMap(session -> {
                    session.setStatus("REVOKED");
                    session.setRevokedAt(Instant.now());
                    session.setNotNew();
                    return sessionRepository.save(session)
                            .flatMap(s -> refreshTokenRepository.deleteBySessionId(s.getId())); // Optionally just revoke instead of delete
                })
                .then(auditService.logAndPublishEvent("USER_LOGOUT_ALL", userId, userId, null, null, ipAddress, userAgent, Map.of()))
                .then();
    }

    @Transactional
    public Mono<AuthResponse> serviceToServiceAuth(ServiceTokenRequest request, String ipAddress, String userAgent) {
        return clientRepository.findByClientId(request.clientId())
                .switchIfEmpty(Mono.error(new IdentityException("Invalid client", "INVALID_CLIENT", HttpStatus.BAD_REQUEST)))
                .flatMap(client -> {
                    if (!"CONFIDENTIAL".equals(client.getClientType()) || client.getClientSecretHash() == null) {
                        return Mono.error(new IdentityException("Invalid client type", "INVALID_CLIENT", HttpStatus.BAD_REQUEST));
                    }
                    return matchesPassword(request.clientSecret(), client.getClientSecretHash())
                            .flatMap(matches -> {
                                if (!matches) {
                                    return auditService.logAndPublishEvent("SERVICE_AUTH_FAILED", client.getId(), null, client.getId(), null, ipAddress, userAgent, Map.of("reason", "bad_secret"))
                                            .then(Mono.error(new IdentityException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED)));
                                }

                                String accessToken = jwtService.generateAccessToken(
                                        client.getId(), client.getClientId(), "SERVICE", List.of("ROLE_SERVICE"), client.getId(), client.getId()
                                );

                                return auditService.logAndPublishEvent("SERVICE_TOKEN_ISSUED", client.getId(), null, client.getId(), null, ipAddress, userAgent, Map.of())
                                        .thenReturn(new AuthResponse(accessToken, "Bearer", 900L));
                            });
                });
    }

    private Mono<LoginResult> createSessionAndTokens(User user, Client client, String ipAddress, String userAgent, String deviceId, String deviceName, String deviceType, String location, UUID familyId) {
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
        session.setLastAccessedAt(Instant.now());

        long refreshTokenTtl = client.getRefreshTokenTtl() != null ? client.getRefreshTokenTtl() : 2592000L;
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
                            refreshToken.setRevoked(false);

                            return refreshTokenRepository.save(refreshToken)
                                    .flatMap(rt -> {
                                        AuthResponse authResponse = new AuthResponse(accessToken, "Bearer", 900L);
                                        return auditService.logAndPublishEvent("USER_LOGIN", user.getId(), user.getId(), client.getId(), savedSession.getId(), ipAddress, userAgent, Map.of())
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
