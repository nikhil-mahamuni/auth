package com.quberratrix.identity.jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
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

        // Ensure proper RFC7517 encoding (unsigned BigInteger to byte array, without leading zeros)
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(toIntegerBytes(publicKey.getPublicExponent()));
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(toIntegerBytes(publicKey.getModulus()));

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "e", e,
                "n", n,
                "kid", jwtService.getKeyId(),
                "alg", "RS256",
                "use", "sig"
        );

        return Mono.just(Map.of("keys", List.of(jwk)));
    }

    private byte[] toIntegerBytes(BigInteger bigInt) {
        int bitlen = bigInt.bitLength();
        // round bitlen
        bitlen = ((bitlen + 7) >> 3) << 3;
        byte[] bigBytes = bigInt.toByteArray();

        if (((bigInt.bitLength() % 8) != 0) && (((bigInt.bitLength() / 8) + 1) == (bitlen / 8))) {
            return bigBytes;
        }

        int startSrc = 0;
        int len = bigBytes.length;

        if ((bigInt.bitLength() % 8) == 0) {
            startSrc = 1;
            len--;
        }

        byte[] resizedBytes = new byte[bitlen / 8];
        int startDst = resizedBytes.length - len;
        System.arraycopy(bigBytes, startSrc, resizedBytes, startDst, len);

        return resizedBytes;
    }
}
