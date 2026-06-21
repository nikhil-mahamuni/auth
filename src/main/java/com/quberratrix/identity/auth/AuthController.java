package com.quberratrix.identity.auth;

import com.quberratrix.identity.common.ApiResponse;
import com.quberratrix.identity.common.CorrelationIdWebFilter;
import com.quberratrix.identity.serviceauth.ServiceAuthService;
import com.quberratrix.identity.serviceauth.ServiceTokenRequest;
import com.quberratrix.identity.tokens.TokenServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenServices tokenServices;
    private final ServiceAuthService serviceAuthService;

    private String getIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUa(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
    }

    private String getCorrelationId(ServerWebExchange exchange) {
        Object correlationId = exchange.getAttributes().get(CorrelationIdWebFilter.CORRELATION_ID_KEY);
        return correlationId != null ? correlationId.toString() : "";
    }

    private String getRequestId(ServerWebExchange exchange) {
        Object requestId = exchange.getAttributes().get(CorrelationIdWebFilter.REQUEST_ID_KEY);
        return requestId != null ? requestId.toString() : "";
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<Map<String, String>>> register(@Valid @RequestBody RegisterRequest request, ServerWebExchange exchange) {
        return authService.register(request, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange))
                .map(user -> ApiResponse.success(Map.of("userId", user.getId().toString()), "User registered successfully"));
    }

    @PostMapping("/auth/login")
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> login(@Valid @RequestBody AuthRequest request, ServerWebExchange exchange) {
        String deviceId = exchange.getRequest().getHeaders().getFirst("X-Device-Id");
        String deviceName = exchange.getRequest().getHeaders().getFirst("X-Device-Name");
        String deviceType = exchange.getRequest().getHeaders().getFirst("X-Device-Type");
        String location = exchange.getRequest().getHeaders().getFirst("X-Location");

        return authService.login(request, getIp(exchange), getUa(exchange), deviceId, deviceName, deviceType, location, getCorrelationId(exchange), getRequestId(exchange))
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
                            .body(ApiResponse.success(result.response()));
                });
    }

    @PostMapping("/auth/refresh")
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshTokenCookie,
            @RequestBody(required = false) RefreshRequest request,
            ServerWebExchange exchange) {

        String tokenToUse = refreshTokenCookie;
        String clientId = request != null ? request.clientId() : null;

        String deviceId = exchange.getRequest().getHeaders().getFirst("X-Device-Id");
        String deviceName = exchange.getRequest().getHeaders().getFirst("X-Device-Name");
        String deviceType = exchange.getRequest().getHeaders().getFirst("X-Device-Type");
        String location = exchange.getRequest().getHeaders().getFirst("X-Location");

        return authService.refreshWithSha256(tokenToUse, clientId, getIp(exchange), getUa(exchange), deviceId, deviceName, deviceType, location, getCorrelationId(exchange), getRequestId(exchange))
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
                            .body(ApiResponse.success(result.response()));
                });
    }

    @PostMapping("/auth/logout")
    public Mono<ResponseEntity<ApiResponse<Void>>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshTokenCookie,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            ServerWebExchange exchange) {

        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        return authService.logout(refreshTokenCookie, accessToken, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange))
                .then(Mono.defer(() -> {
                    ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                            .httpOnly(true)
                            .secure(true)
                            .path("/api/v1/auth/refresh")
                            .maxAge(0)
                            .sameSite("Strict")
                            .build();
                    ApiResponse<Void> body = ApiResponse.success(null, "Logged out successfully");
                    return Mono.just(ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(body));
                }));
    }

    @PostMapping("/auth/service-token")
    public Mono<ApiResponse<AuthResponse>> serviceToken(@Valid @RequestBody ServiceTokenRequest request, ServerWebExchange exchange) {
        return serviceAuthService.serviceToServiceAuth(request, getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange))
                .map(ApiResponse::success);
    }

    @PostMapping("/email/verify")
    public Mono<ApiResponse<Void>> verifyEmail(@Valid @RequestBody EmailVerificationRequest request, ServerWebExchange exchange) {
        return tokenServices.verifyEmail(request.token(), getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange))
                .thenReturn(ApiResponse.success((Void) null, "Email successfully verified."));
    }

    @PostMapping("/password/reset-request")
    public Mono<ApiResponse<Void>> requestReset(@Valid @RequestBody PasswordResetRequest request, ServerWebExchange exchange) {
        ApiResponse<Void> response = ApiResponse.success((Void) null, "If an account exists, a password reset link has been sent.");
        return tokenServices.requestPasswordReset(request.email(), getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange))
                .thenReturn(response)
                .onErrorReturn(response);
    }

    @PostMapping("/password/reset-confirm")
    public Mono<ApiResponse<Void>> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request, ServerWebExchange exchange) {
        return tokenServices.confirmPasswordReset(request.token(), request.newPassword(), getIp(exchange), getUa(exchange), getCorrelationId(exchange), getRequestId(exchange))
                .thenReturn(ApiResponse.success((Void) null, "Password has been successfully reset."));
    }
}
