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
        // adultera um carácter no MEIO da assinatura — o último carácter em
        // base64url tem bits finais descartados na descodificação, pelo que
        // trocá-lo pode não alterar os bytes (teste ficaria flaky)
        int i = token.lastIndexOf('.') + 5;
        char c = token.charAt(i);
        String tampered = token.substring(0, i) + (c == 'a' ? 'b' : 'a') + token.substring(i + 1);
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
