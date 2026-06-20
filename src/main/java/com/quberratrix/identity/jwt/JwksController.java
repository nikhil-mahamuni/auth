package com.quberratrix.identity.jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jwks")
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping
    public Mono<Map<String, Object>> getJwks() {
        RSAPublicKey publicKey = (RSAPublicKey) jwtService.getPublicKey();

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "e", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray()),
                "n", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
                "kid", jwtService.getKeyId(),
                "alg", "RS256",
                "use", "sig"
        );

        return Mono.just(Map.of("keys", List.of(jwk)));
    }
}
