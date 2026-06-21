package com.quberratrix.identity.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Login login = new Login();
    private PasswordReset passwordReset = new PasswordReset();

    @Data
    public static class Login {
        @Min(1)
        private int maxFailedAttempts = 5;
        @NotNull
        private Duration lockDuration = Duration.ofMinutes(15);
    }

    @Data
    public static class PasswordReset {
        private boolean revokeSessionsAfterReset = true;
    }
}
