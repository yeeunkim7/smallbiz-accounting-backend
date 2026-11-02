package com.smallbiz.reconciliation.account;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Explicit browser origins only. Native bearer clients do not need CORS. */
@Configuration
public class BrowserCorsConfiguration {
    @Bean
    UrlBasedCorsConfigurationSource browserCors(@Value("${app.cors.allowed-origins:}") String configured) {
        List<String> origins = Arrays.stream(configured.split(",")).map(String::strip)
                .filter(value -> !value.isEmpty()).distinct().toList();
        for (String origin : origins) {
            try {
                URI uri = URI.create(origin);
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getPort() < -1 || uri.getPort() > 65535
                        || !origin.equals(uri.getScheme() + "://" + uri.getRawAuthority()))
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("CORS requires exact HTTP(S) origins without credentials or paths");
            }
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Retry-After"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
