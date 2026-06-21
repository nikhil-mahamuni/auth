package com.quberratrix.identity.config.properties;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "token")
public class TokenProperties {
    @NotNull
    private Duration accessTokenTtl;
    @NotNull
    private Duration refreshTokenTtl;
    @NotNull
    private Duration emailVerificationTokenTtl;
    @NotNull
    private Duration passwordResetTokenTtl;
}
