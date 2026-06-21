package com.quberratrix.identity.tokens;

import com.quberratrix.identity.audit.AuditService;
import com.quberratrix.identity.common.IdentityException;
import com.quberratrix.identity.users.UserRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenServices {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    private String generateOpaqueToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
    public Mono<Void> requestEmailVerification(String email, String ipAddress, String userAgent, String correlationId, String requestId) {
        return userRepository.findByEmail(email)
                .flatMap(user -> {
                    if (user.isEmailVerified()) return Mono.empty();

                    String rawToken = generateOpaqueToken();
                    String hashedToken = sha256(rawToken);

                    EmailVerificationToken token = new EmailVerificationToken();
                    token.setId(UUID.randomUUID());
                    token.setTokenHash(hashedToken);
                    token.setUserId(user.getId());
                    token.setIssuedAt(Instant.now());
                    token.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
                    token.setUsed(false);

                    return emailVerificationTokenRepository.save(token)
                            .flatMap(saved -> auditService.logAndPublishEvent(
                                    "EMAIL_VERIFICATION_REQUESTED",
                                    user.getId(), user.getId(), null, null, ipAddress, userAgent, correlationId, requestId,
                                    Map.of("email", email, "token", rawToken)
                            ));
                }).then();
    }

    @Transactional
    public Mono<Void> verifyEmail(String rawToken, String ipAddress, String userAgent, String correlationId, String requestId) {
        String hashedToken = sha256(rawToken);
        return emailVerificationTokenRepository.findByTokenHash(hashedToken)
                .switchIfEmpty(Mono.error(new IdentityException("Invalid token", "INVALID_TOKEN", HttpStatus.BAD_REQUEST)))
                .flatMap(token -> {
                    if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
                        return Mono.error(new IdentityException("Token expired or already used", "TOKEN_EXPIRED", HttpStatus.BAD_REQUEST));
                    }

                    token.setUsed(true);
                    token.setNotNew();
                    return emailVerificationTokenRepository.save(token)
                            .then(userRepository.findById(token.getUserId()))
                            .flatMap(user -> {
                                user.setEmailVerified(true);
                                user.setNotNew();
                                return userRepository.save(user);
                            })
                            .flatMap(user -> auditService.logAndPublishEvent(
                                    "USER_VERIFIED",
                                    user.getId(), user.getId(), null, null, ipAddress, userAgent, correlationId, requestId, Map.of()
                            ));
                }).then();
    }

    @Transactional
    public Mono<Void> requestPasswordReset(String email, String ipAddress, String userAgent, String correlationId, String requestId) {
        return userRepository.findByEmail(email)
                .flatMap(user -> {
                    String rawToken = generateOpaqueToken();
                    String hashedToken = sha256(rawToken);

                    PasswordResetToken token = new PasswordResetToken();
                    token.setId(UUID.randomUUID());
                    token.setTokenHash(hashedToken);
                    token.setUserId(user.getId());
                    token.setIssuedAt(Instant.now());
                    token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
                    token.setUsed(false);

                    return passwordResetTokenRepository.save(token)
                            .flatMap(saved -> auditService.logAndPublishEvent(
                                    "PASSWORD_RESET_REQUESTED",
                                    user.getId(), user.getId(), null, null, ipAddress, userAgent, correlationId, requestId,
                                    Map.of("email", email, "token", rawToken)
                            ));
                }).then();
    }

    @Transactional
    public Mono<Void> confirmPasswordReset(String rawToken, String newPassword, String ipAddress, String userAgent, String correlationId, String requestId) {
        String hashedToken = sha256(rawToken);
        return passwordResetTokenRepository.findByTokenHash(hashedToken)
                .switchIfEmpty(Mono.error(new IdentityException("Invalid token", "INVALID_TOKEN", HttpStatus.BAD_REQUEST)))
                .flatMap(token -> {
                    if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
                        return Mono.error(new IdentityException("Token expired or already used", "TOKEN_EXPIRED", HttpStatus.BAD_REQUEST));
                    }

                    token.setUsed(true);
                    token.setNotNew();
                    return passwordResetTokenRepository.save(token)
                            .then(userRepository.findById(token.getUserId()))
                            .flatMap(user -> Mono.fromCallable(() -> passwordEncoder.encode(newPassword))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .flatMap(hashedPassword -> {
                                        user.setPasswordHash(hashedPassword);
                                        user.setPasswordUpdatedAt(Instant.now());
                                        user.setLockedUntil(null);
                                        user.setFailedLoginAttempts(0);
                                        user.setNotNew();
                                        return userRepository.save(user);
                                    })
                            )
                            .flatMap(user -> auditService.logAndPublishEvent(
                                    "PASSWORD_RESET",
                                    user.getId(), user.getId(), null, null, ipAddress, userAgent, correlationId, requestId, Map.of()
                            ));
                }).then();
    }
}
