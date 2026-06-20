package com.quberratrix.identity.service;

import com.quberratrix.identity.dto.AuthRequest;
import com.quberratrix.identity.dto.AuthResponse;
import com.quberratrix.identity.dto.RegisterRequest;
import com.quberratrix.identity.dto.ServiceTokenRequest;
import com.quberratrix.identity.entity.Client;
import com.quberratrix.identity.entity.RefreshToken;
import com.quberratrix.identity.entity.Role;
import com.quberratrix.identity.entity.Session;
import com.quberratrix.identity.entity.User;
import com.quberratrix.identity.event.IdentityEventPublisher;
import com.quberratrix.identity.exception.IdentityException;
import com.quberratrix.identity.jwt.JwtService;
import com.quberratrix.identity.repository.ClientRepository;
import com.quberratrix.identity.repository.RefreshTokenRepository;
import com.quberratrix.identity.repository.RoleRepository;
import com.quberratrix.identity.repository.SessionRepository;
import com.quberratrix.identity.repository.UserRepository;
import com.quberratrix.identity.repository.UserRoleRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final IdentityEventPublisher eventPublisher;

    private Mono<String> encodePassword(String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoder.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> matchesPassword(String rawPassword, String encodedPassword) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, encodedPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<User> register(RegisterRequest request) {
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
                            newUser.setUserType("PUBLIC");
                            newUser.setStatus("ACTIVE");
                            newUser.setCreatedAt(Instant.now());
                            newUser.setUpdatedAt(Instant.now());

                            return userRepository.save(newUser)
                                    .flatMap(savedUser -> roleRepository.findByName("ROLE_USER")
                                            .flatMap(role -> userRoleRepository.assignRole(savedUser.getId(), role.getId()))
                                            .thenReturn(savedUser)
                                    )
                                    .flatMap(savedUser -> eventPublisher.publishEvent(
                                            "identity.user.registered",
                                            savedUser.getId(),
                                            savedUser.getId(),
                                            null, null,
                                            Map.of("email", savedUser.getEmail())
                                    ).thenReturn(savedUser));
                        })));
    }

    @Transactional
    public Mono<LoginResult> login(AuthRequest request, String ipAddress, String userAgent) {
        return clientRepository.findByClientId(request.clientId() != null ? request.clientId() : "dev-client")
                .switchIfEmpty(Mono.error(new IdentityException("Invalid client", "INVALID_CLIENT", HttpStatus.BAD_REQUEST)))
                .flatMap(client -> {
                    if (!client.isEnabled()) {
                        return Mono.error(new IdentityException("Client is disabled", "CLIENT_DISABLED", HttpStatus.FORBIDDEN));
                    }
                    return userRepository.findByEmail(request.email())
                            .switchIfEmpty(Mono.error(new IdentityException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED)))
                            .flatMap(user -> {
                                if ("LOCKED".equals(user.getStatus())) {
                                    return Mono.error(new IdentityException("Account is locked", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN));
                                }
                                return matchesPassword(request.password(), user.getPasswordHash())
                                        .flatMap(matches -> {
                                            if (!matches) {
                                                return eventPublisher.publishEvent("identity.login.failed", user.getId(), user.getId(), client.getId(), null, Map.of("reason", "bad_password"))
                                                        .then(Mono.error(new IdentityException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED)));
                                            }
                                            return createSessionAndTokens(user, client, ipAddress, userAgent);
                                        });
                            });
                });
    }

    // Using SHA-256 for refresh tokens instead of BCrypt so we can query them
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
    public Mono<LoginResult> refreshWithSha256(String rawRefreshToken, String clientId, String ipAddress, String userAgent) {
         if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Mono.error(new IdentityException("Missing refresh token", "MISSING_TOKEN", HttpStatus.UNAUTHORIZED));
        }

        String hashedToken = sha256(rawRefreshToken);

        return refreshTokenRepository.findByTokenHash(hashedToken)
                .switchIfEmpty(Mono.error(new IdentityException("Invalid refresh token", "INVALID_TOKEN", HttpStatus.UNAUTHORIZED)))
                .flatMap(refreshToken -> {
                    if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
                        return Mono.error(new IdentityException("Token expired or revoked", "TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED));
                    }

                    return clientRepository.findById(refreshToken.getClientId())
                            .flatMap(client -> userRepository.findById(refreshToken.getUserId())
                                    .flatMap(user -> {
                                         if ("LOCKED".equals(user.getStatus()) || !client.isEnabled()) {
                                              return Mono.error(new IdentityException("Account locked or client disabled", "ACCOUNT_LOCKED", HttpStatus.FORBIDDEN));
                                         }

                                         // Invalidate old token
                                         refreshToken.setRevoked(true);
                                         return refreshTokenRepository.save(refreshToken)
                                                 .then(createSessionAndTokens(user, client, ipAddress, userAgent));
                                    }));
                });
    }

    @Transactional
    public Mono<Void> logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Mono.empty();
        }
        String hashedToken = sha256(rawRefreshToken);
        return refreshTokenRepository.findByTokenHash(hashedToken)
                .flatMap(token -> {
                    token.setRevoked(true);
                    return refreshTokenRepository.save(token)
                            .flatMap(t -> sessionRepository.findById(t.getSessionId()))
                            .flatMap(session -> {
                                session.setStatus("REVOKED");
                                return sessionRepository.save(session);
                            })
                            .flatMap(s -> eventPublisher.publishEvent("identity.user.logged_out", token.getUserId(), token.getUserId(), token.getClientId(), token.getSessionId(), Map.of()));
                }).then();
    }

    @Transactional
    public Mono<Void> logoutAllSessions(UUID userId) {
        return sessionRepository.findByUserIdAndStatus(userId, "ACTIVE")
                .flatMap(session -> {
                    session.setStatus("REVOKED");
                    return sessionRepository.save(session);
                })
                .then(eventPublisher.publishEvent("identity.user.logged_out_all", userId, userId, null, null, Map.of()))
                .then();
    }

    @Transactional
    public Mono<AuthResponse> serviceToServiceAuth(ServiceTokenRequest request) {
        return clientRepository.findByClientId(request.clientId())
                .switchIfEmpty(Mono.error(new IdentityException("Invalid client", "INVALID_CLIENT", HttpStatus.BAD_REQUEST)))
                .flatMap(client -> {
                    if (!"CONFIDENTIAL".equals(client.getClientType()) || client.getClientSecretHash() == null) {
                        return Mono.error(new IdentityException("Invalid client type", "INVALID_CLIENT", HttpStatus.BAD_REQUEST));
                    }
                    return matchesPassword(request.clientSecret(), client.getClientSecretHash())
                            .flatMap(matches -> {
                                if (!matches) {
                                    return Mono.error(new IdentityException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED));
                                }

                                String accessToken = jwtService.generateAccessToken(
                                        client.getId(), client.getClientId(), "SERVICE", List.of("ROLE_SERVICE"), client.getId(), client.getId()
                                );

                                return eventPublisher.publishEvent("identity.service.token_issued", client.getId(), null, client.getId(), null, Map.of())
                                        .thenReturn(new AuthResponse(accessToken, "Bearer", 900L));
                            });
                });
    }

    private Mono<LoginResult> createSessionAndTokens(User user, Client client, String ipAddress, String userAgent) {
        Session session = new Session();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setClientId(client.getId());
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
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

                            return refreshTokenRepository.save(refreshToken)
                                    .flatMap(rt -> {
                                        AuthResponse authResponse = new AuthResponse(accessToken, "Bearer", 900L);
                                        return eventPublisher.publishEvent("identity.user.logged_in", user.getId(), user.getId(), client.getId(), savedSession.getId(), Map.of())
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
