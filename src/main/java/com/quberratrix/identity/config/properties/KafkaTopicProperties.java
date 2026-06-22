package com.quberratrix.identity.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "identity.kafka.topics")
public class KafkaTopicProperties {
    private String userRegistered = "identity-user-registered";
    private String userVerified = "identity-user-verified";
    private String userLogin = "identity-user-login";
    private String userLogout = "identity-user-logout";
    private String passwordReset = "identity-password-reset";
    private String sessionRevoked = "identity-session-revoked";
    private String tokenRefreshed = "identity-token-refreshed";
    private String tokenReused = "identity-token-reused";
    private String defaultTopic = "identity-events";
}
