package com.quberratrix.identity.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "cookie.refresh")
public class CookieProperties {
    private boolean secure = true;
    private boolean httpOnly = true;
    private String sameSite = "Strict";
    private String path = "/api/v1/auth/refresh";
    private String domain;
}
