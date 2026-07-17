package com.tracky.currency;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a lógica pura do CurrencyService (suporte, normalização e o atalho EUR
 * que não faz rede). A obtenção de câmbio via Yahoo não é exercida aqui.
 */
class CurrencyServiceTest {

    private final CurrencyService service = new CurrencyService();

    @Test
    void isSupportedReconheceMoedasConhecidasEIgnoraMaiusculas() {
        assertThat(service.isSupported("eur")).isTrue();
        assertThat(service.isSupported("USD")).isTrue();
        assertThat(service.isSupported("gbp")).isTrue();
        assertThat(service.isSupported("XYZ")).isFalse();
        assertThat(service.isSupported(null)).isFalse();
    }

    @Test
    void normalizeDevolveEurParaEntradasInvalidasOuNulas() {
        assertThat(service.normalize(null)).isEqualTo("EUR");
        assertThat(service.normalize("  ")).isEqualTo("EUR");
        assertThat(service.normalize("moeda-invalida")).isEqualTo("EUR");
    }

    @Test
    void normalizeArrumaMaiusculasEEspacos() {
        assertThat(service.normalize("  usd ")).isEqualTo("USD");
        assertThat(service.normalize("gbp")).isEqualTo("GBP");
    }

    @Test
    void rateFromEurParaEurNaoFazRedeEDevolveUm() {
        assertThat(service.rateFromEur("EUR")).contains(BigDecimal.ONE);
        assertThat(service.rateFromEur("eur")).contains(BigDecimal.ONE);
        assertThat(service.rateFromEur(null)).contains(BigDecimal.ONE);
        assertThat(service.rateFromEur("   ")).contains(BigDecimal.ONE);
    }

    @Test
    void convertFromEurComEurDevolveOMesmoValor() {
        BigDecimal v = new BigDecimal("123.45");
        assertThat(service.convertFromEur(v, "EUR")).isEqualByComparingTo(v);
    }

    @Test
    void convertFromEurComNullDevolveNull() {
        assertThat(service.convertFromEur(null, "USD")).isNull();
    }

    @Test
    void listaDeMoedasSuportadasContemEurComoReferencia() {
        assertThat(CurrencyService.SUPPORTED).contains("EUR", "USD", "GBP", "BRL");
    }
}
