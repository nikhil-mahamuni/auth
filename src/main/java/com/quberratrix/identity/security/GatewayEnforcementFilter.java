package com.quberratrix.identity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GatewayEnforcementFilter implements WebFilter {

    @Value("${security.gateway.enforce:false}")
    private boolean enforceGateway;

    @Value("${security.gateway.secret:}")
    private String gatewaySecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enforceGateway) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || path.startsWith("/api/v1/jwks")) {
            return chain.filter(exchange);
        }

        String incomingSecret = exchange.getRequest().getHeaders().getFirst("X-Gateway-Secret");
        if (incomingSecret == null || !incomingSecret.equals(gatewaySecret)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
