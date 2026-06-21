package com.quberratrix.identity.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank String email
) {}
