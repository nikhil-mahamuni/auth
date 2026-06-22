package com.quberratrix.identity.serviceauth;

import com.quberratrix.identity.audit.AuditService;
import com.quberratrix.identity.auth.AuthResponse;
import com.quberratrix.identity.clients.ClientRepository;
import com.quberratrix.identity.common.IdentityException;
import com.quberratrix.identity.config.properties.TokenProperties;
import com.quberratrix.identity.jwks.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServiceAuthService {

    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final TokenProperties tokenProperties;

    private Mono<Boolean> matchesPassword(String rawPassword, String encodedPassword) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, encodedPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<AuthResponse> serviceToServiceAuth(ServiceTokenRequest request, String ipAddress, String userAgent, String correlationId, String requestId) {
        return clientRepository.findByClientId(request.clientId())
                .switchIfEmpty(Mono.error(new IdentityException("Invalid client", "INVALID_CLIENT", HttpStatus.BAD_REQUEST)))
                .flatMap(client -> {
                    if (!"CONFIDENTIAL".equals(client.getClientType()) && !"SERVICE".equals(client.getClientType()) || client.getClientSecretHash() == null || !"ACTIVE".equals(client.getStatus())) {
                        return Mono.error(new IdentityException("Invalid client type or disabled", "INVALID_CLIENT", HttpStatus.BAD_REQUEST));
                    }
                    return matchesPassword(request.clientSecret(), client.getClientSecretHash())
                            .flatMap(matches -> {
                                if (!matches) {
                                    return auditService.logAndPublishEvent("SERVICE_AUTH_FAILED", client.getId(), null, client.getId(), null, ipAddress, userAgent, correlationId, requestId, Map.of("reason", "bad_secret"))
                                            .then(Mono.error(new IdentityException("Invalid credentials", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED)));
                                }

                                Long clientTtl = client.getAccessTokenTtlSeconds() != null ? Long.valueOf(client.getAccessTokenTtlSeconds()) : null;
                                String accessToken = jwtService.generateServiceToken(
                                        client.getId(), List.of("ROLE_SERVICE"), clientTtl
                                );

                                long ttlResponse = clientTtl != null ? clientTtl : tokenProperties.getAccessTokenTtl().getSeconds();

                                return auditService.logAndPublishEvent("SERVICE_TOKEN_ISSUED", client.getId(), null, client.getId(), null, ipAddress, userAgent, correlationId, requestId, Map.of())
                                        .thenReturn(new AuthResponse(accessToken, "Bearer", ttlResponse));
                            });
                });
    }
}
