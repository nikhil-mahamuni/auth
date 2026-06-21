package com.quberratrix.identity.controller;

import com.quberratrix.identity.dto.PasswordResetConfirmRequest;
import com.quberratrix.identity.dto.PasswordResetRequest;
import com.quberratrix.identity.service.TokenServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final TokenServices tokenServices;

    private String getIp(ServerHttpRequest request) {
        return request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUa(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }

    @PostMapping("/reset-request")
    public Mono<Map<String, String>> requestReset(@Valid @RequestBody PasswordResetRequest request, ServerHttpRequest httpRequest) {
        return tokenServices.requestPasswordReset(request.email(), getIp(httpRequest), getUa(httpRequest))
                .thenReturn(Map.of("message", "If an account exists, a password reset link has been sent."))
                // Always return success to prevent email enumeration
                .onErrorReturn(Map.of("message", "If an account exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-confirm")
    public Mono<Map<String, String>> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request, ServerHttpRequest httpRequest) {
        return tokenServices.confirmPasswordReset(request.token(), request.newPassword(), getIp(httpRequest), getUa(httpRequest))
                .thenReturn(Map.of("message", "Password has been successfully reset."));
    }
}
