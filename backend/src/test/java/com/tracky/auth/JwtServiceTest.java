package com.tracky.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-0123456789-0123456789-0123456789";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void gerarEValidarDevolveOMesmoUserId() {
        String token = jwtService.generate(42L);
        assertThat(jwtService.validate(token)).contains(42L);
    }

    @Test
    void tokenAdulteradoERejeitado() {
        String token = jwtService.generate(42L);
        // troca o último carácter da assinatura
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');
        assertThat(jwtService.validate(tampered)).isEmpty();
    }

    @Test
    void tokenAssinadoComOutroSegredoERejeitado() {
        String other = new JwtService("outro-segredo-9876543210-9876543210-9876543210").generate(42L);
        assertThat(jwtService.validate(other)).isEmpty();
    }

    @Test
    void lixoERejeitadoSemExcecao() {
        assertThat(jwtService.validate("nao-e-um-jwt")).isEqualTo(Optional.empty());
        assertThat(jwtService.validate("")).isEmpty();
        assertThat(jwtService.validate(null)).isEmpty();
    }
}
