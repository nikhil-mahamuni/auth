package com.quberratrix.identity.controller;

import com.quberratrix.identity.dto.EmailVerificationRequest;
import com.quberratrix.identity.service.TokenServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final TokenServices tokenServices;

    private String getIp(ServerHttpRequest request) {
        return request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUa(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }

    @PostMapping("/verify")
    public Mono<Map<String, String>> verifyEmail(@Valid @RequestBody EmailVerificationRequest request, ServerHttpRequest httpRequest) {
        return tokenServices.verifyEmail(request.token(), getIp(httpRequest), getUa(httpRequest))
                .thenReturn(Map.of("message", "Email successfully verified."));
    }
}
