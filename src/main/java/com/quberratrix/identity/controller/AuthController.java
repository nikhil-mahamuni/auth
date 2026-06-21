package com.quberratrix.identity.controller;

import com.quberratrix.identity.dto.AuthRequest;
import com.quberratrix.identity.dto.AuthResponse;
import com.quberratrix.identity.dto.RefreshRequest;
import com.quberratrix.identity.dto.RegisterRequest;
import com.quberratrix.identity.dto.ServiceTokenRequest;
import com.quberratrix.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    private String getIp(ServerHttpRequest request) {
        return request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUa(ServerHttpRequest request) {
        return request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, String>> register(@Valid @RequestBody RegisterRequest request, ServerHttpRequest httpRequest) {
        return authService.register(request, getIp(httpRequest), getUa(httpRequest))
                .map(user -> Map.of(
                        "message", "User registered successfully",
                        "userId", user.getId().toString()
                ));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody AuthRequest request, ServerHttpRequest httpRequest) {
        String deviceId = httpRequest.getHeaders().getFirst("X-Device-Id");
        String deviceName = httpRequest.getHeaders().getFirst("X-Device-Name");
        String deviceType = httpRequest.getHeaders().getFirst("X-Device-Type");
        String location = httpRequest.getHeaders().getFirst("X-Location");

        return authService.login(request, getIp(httpRequest), getUa(httpRequest), deviceId, deviceName, deviceType, location)
                .map(result -> {
                    ResponseCookie cookie = ResponseCookie.from("refresh_token", result.rawRefreshToken())
                            .httpOnly(true)
                            .secure(true)
                            .path("/api/v1/auth/refresh")
                            .maxAge(result.maxAge())
                            .sameSite("Strict")
                            .build();

                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(result.response());
                });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<AuthResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshTokenCookie,
            @RequestBody(required = false) RefreshRequest request,
            ServerHttpRequest httpRequest) {

        String tokenToUse = refreshTokenCookie;
        String clientId = request != null ? request.clientId() : null;

        String deviceId = httpRequest.getHeaders().getFirst("X-Device-Id");
        String deviceName = httpRequest.getHeaders().getFirst("X-Device-Name");
        String deviceType = httpRequest.getHeaders().getFirst("X-Device-Type");
        String location = httpRequest.getHeaders().getFirst("X-Location");

        return authService.refreshWithSha256(tokenToUse, clientId, getIp(httpRequest), getUa(httpRequest), deviceId, deviceName, deviceType, location)
                .map(result -> {
                    ResponseCookie cookie = ResponseCookie.from("refresh_token", result.rawRefreshToken())
                            .httpOnly(true)
                            .secure(true)
                            .path("/api/v1/auth/refresh")
                            .maxAge(result.maxAge())
                            .sameSite("Strict")
                            .build();

                    return ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(result.response());
                });
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshTokenCookie,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            ServerHttpRequest httpRequest) {

        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        return authService.logout(refreshTokenCookie, accessToken, getIp(httpRequest), getUa(httpRequest))
                .then(Mono.defer(() -> {
                    ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                            .httpOnly(true)
                            .secure(true)
                            .path("/api/v1/auth/refresh")
                            .maxAge(0) // Expire immediately
                            .sameSite("Strict")
                            .build();
                    return Mono.just(ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .build());
                }));
    }

    @PostMapping("/service-token")
    public Mono<AuthResponse> serviceToken(@Valid @RequestBody ServiceTokenRequest request, ServerHttpRequest httpRequest) {
        return authService.serviceToServiceAuth(request, getIp(httpRequest), getUa(httpRequest));
    }
}
