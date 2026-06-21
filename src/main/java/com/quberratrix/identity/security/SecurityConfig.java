package com.quberratrix.identity.security;

import com.quberratrix.identity.config.properties.SecurityCorsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityCorsProperties corsProperties;
    private final JwtAuthenticationManager jwtAuthenticationManager;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final GatewayEnforcementFilter gatewayEnforcementFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(jwtAuthenticationManager);
        jwtFilter.setServerAuthenticationConverter(jwtAuthenticationConverter);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/v1/auth/**", "/api/v1/jwks", "/actuator/**", "/api/v1/password/**", "/api/v1/email/**").permitAll()
                        .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .addFilterAt(gatewayEnforcementFilter, SecurityWebFiltersOrder.FIRST)
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> allowedOrigins = corsProperties.getAllowedOrigins();
        if (corsProperties.isAllowCredentials() && allowedOrigins.contains("*")) {
            throw new IllegalStateException("CORS configured incorrectly: cannot use wildcard origins with allow-credentials=true in production");
        }

        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
