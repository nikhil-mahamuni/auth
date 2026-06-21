package com.quberratrix.identity.security;

import com.quberratrix.identity.config.properties.GatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GatewayEnforcementFilter implements WebFilter {

    private final GatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!gatewayProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();

        // Exclude configured paths completely
        for (String excludedPath : gatewayProperties.getExcludedPaths()) {
            if (path.startsWith(excludedPath)) {
                return chain.filter(exchange);
            }
        }

        String incomingName = exchange.getRequest().getHeaders().getFirst(gatewayProperties.getTrustedNameHeader());
        String incomingSecret = exchange.getRequest().getHeaders().getFirst(gatewayProperties.getTrustedSecretHeader());

        if (incomingName == null || !incomingName.equals(gatewayProperties.getExpectedName()) ||
            incomingSecret == null || !incomingSecret.equals(gatewayProperties.getExpectedSecret())) {

            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }
}
