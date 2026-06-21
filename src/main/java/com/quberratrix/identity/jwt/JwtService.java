package com.quberratrix.identity.jwt;

import com.quberratrix.identity.exception.IdentityException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.audience}")
    private String audience;

    @Value("${security.jwt.access-token-ttl-seconds}")
    private long accessTokenTtlSeconds;

    @Value("${security.jwt.private-key-path}")
    private String privateKeyPath;

    @Value("${security.jwt.public-key-path}")
    private String publicKeyPath;

    @Value("${security.jwt.dev-generate-keypair}")
    private boolean devGenerateKeypair;

    @Value("${security.jwt.kid:default-kid-1}")
    private String configuredKid;

    private final ResourceLoader resourceLoader;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String keyId;

    public JwtService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            loadKeys();
            keyId = configuredKid;
            log.info("JWT keys initialized successfully with kid: {}", keyId);
        } catch (Exception e) {
            if (devGenerateKeypair) {
                log.warn("Failed to load keys from paths, generating dev keypair...");
                generateDevKeys();
            } else {
                log.error("Failed to load JWT keys and dev generation is disabled", e);
                throw new IllegalStateException("Failed to initialize JWT keys", e);
            }
        }
    }

    private void loadKeys() throws Exception {
        Resource privateKeyRes = resourceLoader.getResource(privateKeyPath);
        Resource publicKeyRes = resourceLoader.getResource(publicKeyPath);

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

        try (PEMParser privatePemParser = new PEMParser(new InputStreamReader(privateKeyRes.getInputStream()))) {
            Object object = privatePemParser.readObject();
            if (object instanceof PEMKeyPair) {
                KeyPair kp = converter.getKeyPair((PEMKeyPair) object);
                this.privateKey = kp.getPrivate();
            } else if (object instanceof PrivateKeyInfo) {
                this.privateKey = converter.getPrivateKey((PrivateKeyInfo) object);
            } else {
                throw new IllegalStateException("Unsupported private key format");
            }
        }

        try (PEMParser publicPemParser = new PEMParser(new InputStreamReader(publicKeyRes.getInputStream()))) {
            Object object = publicPemParser.readObject();
            if (object instanceof SubjectPublicKeyInfo) {
                this.publicKey = converter.getPublicKey((SubjectPublicKeyInfo) object);
            } else {
                throw new IllegalStateException("Unsupported public key format");
            }
        }
    }

    private void generateDevKeys() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
            this.keyId = configuredKid;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate dev keys", e);
        }
    }

    public String generateAccessToken(UUID userId, String email, String userType, java.util.List<String> roles, UUID clientId, UUID sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer)
                .subject(userId.toString())
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString()) // jti
                .issuedAt(Date.from(now)) // iat
                .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds))) // exp
                .claims(Map.of(
                        "email", email,
                        "typ", "Bearer",
                        "roles", roles,
                        "client_id", clientId.toString(),
                        "sid", sessionId.toString()
                ))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new IdentityException("Invalid or expired access token", "INVALID_TOKEN", HttpStatus.UNAUTHORIZED);
        }
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return keyId;
    }
}
