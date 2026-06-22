package com.quberratrix.identity.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "identity.jwt")
public class JwtProperties {
    @NotBlank
    private String issuer;
    @NotBlank
    private String audience;
    @NotBlank
    private String kid;
    private String privateKeyPath;
    private String publicKeyPath;
    private boolean devGenerateKeypair = false;
}
