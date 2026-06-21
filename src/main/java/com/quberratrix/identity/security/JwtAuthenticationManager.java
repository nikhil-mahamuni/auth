package com.quberratrix.identity.security;

import com.quberratrix.identity.jwks.JwtService;
import com.quberratrix.identity.tokens.RevokedAccessTokenRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Authentication> authenticate(Authentication authentication) {
        String authToken = authentication.getCredentials().toString();
        Claims claims;
        try {
            claims = jwtService.validateToken(authToken);
        } catch (Exception e) {
            return Mono.empty();
        }

        String jti = claims.getId();
        if (jti == null) {
            return Mono.empty();
        }

        return revokedAccessTokenRepository.existsByJti(UUID.fromString(jti))
                .flatMap(revoked -> {
                    if (revoked) {
                        return Mono.empty();
                    }

                    String subject = claims.getSubject();
                    List<String> roles = claims.get("roles", List.class);
                    List<SimpleGrantedAuthority> authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());

                    return Mono.just(new UsernamePasswordAuthenticationToken(subject, authToken, authorities));
                });
    }
}
