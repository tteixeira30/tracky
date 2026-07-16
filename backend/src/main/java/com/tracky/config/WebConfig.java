package com.tracky.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class WebConfig {

    /**
     * Origens permitidas para CORS, separadas por vírgulas (configurável via
     * TRACKY_CORS_ORIGINS). O default de desenvolvimento permite tudo ("*") e
     * lista explicitamente as origens da WebView do Capacitor (http/https
     * "localhost" sem porta em Android; "capacitor://localhost" em iOS) para
     * servir de referência ao configurar produção.
     */
    @Value("${tracky.cors-origins}")
    private String corsOrigins;

    /**
     * Configuração CORS consumida pela cadeia do Spring Security (ver
     * SecurityConfig, http.cors()). Tem de ser tratada ao nível do Security e
     * não do MVC: o preflight OPTIONS nunca leva o header Authorization, pelo
     * que seria barrado com 401/403 antes de chegar ao MVC — foi exatamente o
     * que impedia a app mobile (Capacitor, cross-origin) de carregar dados.
     * O site web nunca sofreu disto porque é same-origin.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
        CorsConfiguration cfg = new CorsConfiguration();
        // allowedOriginPatterns em vez de allowedOrigins: aceita "*" misturado
        // com origens concretas e não parte se um dia se ativar allowCredentials.
        cfg.setAllowedOriginPatterns(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setMaxAge(1800L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
