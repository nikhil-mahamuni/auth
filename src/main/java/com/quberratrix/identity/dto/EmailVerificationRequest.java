package com.quberratrix.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationRequest(
        @NotBlank String token
) {}
