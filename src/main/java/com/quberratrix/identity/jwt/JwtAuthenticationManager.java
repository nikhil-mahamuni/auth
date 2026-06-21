package com.quberratrix.identity.jwt;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;
    private final RevokedTokenValidator revokedTokenValidator;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String authToken = authentication.getCredentials().toString();
        Claims claims;
        try {
            claims = jwtService.validateToken(authToken);
        } catch (Exception e) {
            return Mono.empty(); // Let the framework handle unauthenticated
        }

        String jti = claims.getId();

        return revokedTokenValidator.isTokenRevoked(jti)
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
