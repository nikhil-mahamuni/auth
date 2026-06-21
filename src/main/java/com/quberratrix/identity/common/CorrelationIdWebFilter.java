package com.quberratrix.identity.common;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdWebFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String REQUEST_ID_KEY = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        exchange.getAttributes().put(CORRELATION_ID_KEY, correlationId);
        exchange.getAttributes().put(REQUEST_ID_KEY, requestId);

        // Optional: add them to response headers as well
        String finalCorrelationId = correlationId;
        String finalRequestId = requestId;
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, finalCorrelationId);
            exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, finalRequestId);
            return Mono.empty();
        });

        // Can also add them to MDC context if logging is configured for reactive MDC context
        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(CORRELATION_ID_KEY, finalCorrelationId).put(REQUEST_ID_KEY, finalRequestId));
    }
}
