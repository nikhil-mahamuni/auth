package com.quberratrix.identity.serviceauth;

import jakarta.validation.constraints.NotBlank;

public record ServiceTokenRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret
) {}
