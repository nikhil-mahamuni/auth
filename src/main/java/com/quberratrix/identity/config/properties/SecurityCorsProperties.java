package com.quberratrix.identity.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "identity.security.cors")
public class SecurityCorsProperties {
    private List<String> allowedOrigins = List.of();
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "X-Requested-With", "X-Gateway-Name", "X-Gateway-Secret", "X-Device-Id", "X-Device-Name", "X-Device-Type", "X-Location", "X-Correlation-Id", "X-Request-Id");
    private boolean allowCredentials = false;
}
