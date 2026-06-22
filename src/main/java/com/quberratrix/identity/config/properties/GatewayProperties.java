package com.quberratrix.identity.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "identity.gateway")
public class GatewayProperties {
    private boolean enabled = false;
    private String trustedNameHeader;
    private String trustedSecretHeader;
    private String expectedName;
    private String expectedSecret;
    private List<String> excludedPaths = List.of();
}
