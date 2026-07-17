package com.tracky.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Testa a construção da configuração CORS a partir da lista de origens. */
class WebConfigTest {

    private CorsConfiguration configFor(String origins) {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "corsOrigins", origins);
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        return Objects.requireNonNull(source.getCorsConfigurations().get("/api/**"));
    }

    @Test
    void separaOrigensPorVirgulaEIgnoraEspacosEEntradasVazias() {
        CorsConfiguration cfg = configFor(" https://app.tracky.pt , capacitor://localhost ,, ");

        assertThat(cfg.getAllowedOriginPatterns())
                .containsExactly("https://app.tracky.pt", "capacitor://localhost");
    }

    @Test
    void permiteOsMetodosHttpUsadosPelaApiEDefineMaxAge() {
        CorsConfiguration cfg = configFor("*");

        assertThat(cfg.getAllowedOriginPatterns()).containsExactly("*");
        assertThat(cfg.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cfg.getAllowedHeaders()).containsExactly("*");
        assertThat(cfg.getMaxAge()).isEqualTo(1800L);
    }
}
