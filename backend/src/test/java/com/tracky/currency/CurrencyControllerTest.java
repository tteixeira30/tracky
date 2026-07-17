package com.tracky.currency;

import com.tracky.auth.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Testa a resposta do CurrencyController com o serviço de câmbio mockado. */
@ExtendWith(MockitoExtension.class)
class CurrencyControllerTest {

    @Mock CurrencyService currencyService;

    private User userWithCurrency(String base) {
        User u = mock(User.class);
        when(u.getBaseCurrency()).thenReturn(base);
        return u;
    }

    @Test
    void infoComTaxaLiveDevolveRateComSeisCasasERateLiveTrue() {
        var controller = new CurrencyController(currencyService);
        when(currencyService.rateFromEur("USD")).thenReturn(Optional.of(new BigDecimal("1.0937")));

        var info = controller.info(userWithCurrency("USD"));

        assertThat(info.base()).isEqualTo("USD");
        assertThat(info.rate()).isEqualByComparingTo("1.093700");
        assertThat(info.rate().scale()).isEqualTo(6);
        assertThat(info.rateLive()).isTrue();
        assertThat(info.supported()).isEqualTo(CurrencyService.SUPPORTED);
    }

    @Test
    void infoSemTaxaCaiParaUmEMarcaRateLiveFalse() {
        var controller = new CurrencyController(currencyService);
        when(currencyService.rateFromEur("GBP")).thenReturn(Optional.empty());

        var info = controller.info(userWithCurrency("GBP"));

        assertThat(info.rate()).isEqualByComparingTo("1.000000");
        assertThat(info.rateLive()).isFalse();
    }
}
