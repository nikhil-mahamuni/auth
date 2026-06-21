package com.quberratrix.identity.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private String getRequestId(ServerWebExchange exchange) {
        Object requestId = exchange.getAttributes().get(CorrelationIdWebFilter.REQUEST_ID_KEY);
        return requestId != null ? requestId.toString() : "";
    }

    private String getCorrelationId(ServerWebExchange exchange) {
        Object correlationId = exchange.getAttributes().get(CorrelationIdWebFilter.CORRELATION_ID_KEY);
        return correlationId != null ? correlationId.toString() : "";
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdentityException(IdentityException ex, ServerWebExchange exchange) {
        ApiResponse<Void> response = new ApiResponse<>(
                false, ex.getErrorCode(), ex.getMessage(), null, null, Instant.now(), getRequestId(exchange), getCorrelationId(exchange)
        );
        return new ResponseEntity<>(response, ex.getStatus());
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(WebExchangeBindException ex, ServerWebExchange exchange) {
        Map<String, String> errors = new HashMap<>();
        ex.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ApiResponse<Void> response = new ApiResponse<>(
                false, "VALIDATION_FAILED", "Validation failed", null, errors, Instant.now(), getRequestId(exchange), getCorrelationId(exchange)
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, ServerWebExchange exchange) {
        ApiResponse<Void> response = new ApiResponse<>(
                false, "INTERNAL_SERVER_ERROR", "An unexpected error occurred", null, null, Instant.now(), getRequestId(exchange), getCorrelationId(exchange)
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
