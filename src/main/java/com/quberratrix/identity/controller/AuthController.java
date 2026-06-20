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

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request)
                .map(user -> Map.of(
                        "message", "User registered successfully",
                        "userId", user.getId().toString()
                ));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody AuthRequest request, ServerHttpRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddress() != null ? httpRequest.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        String userAgent = httpRequest.getHeaders().getFirst(HttpHeaders.USER_AGENT);

        return authService.login(request, ipAddress, userAgent)
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
        // In case of non-browser client passing token in body (optional fallback)
        // If they pass via body we should probably not enforce cookie, but for strictly web we do.

        String ipAddress = httpRequest.getRemoteAddress() != null ? httpRequest.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        String userAgent = httpRequest.getHeaders().getFirst(HttpHeaders.USER_AGENT);
        String clientId = request != null ? request.clientId() : null;

        return authService.refreshWithSha256(tokenToUse, clientId, ipAddress, userAgent)
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
    public Mono<ResponseEntity<Void>> logout(@CookieValue(name = "refresh_token", required = false) String refreshTokenCookie) {
        return authService.logout(refreshTokenCookie)
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
    public Mono<AuthResponse> serviceToken(@Valid @RequestBody ServiceTokenRequest request) {
        return authService.serviceToServiceAuth(request);
    }
}
