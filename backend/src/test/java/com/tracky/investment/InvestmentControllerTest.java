package com.tracky.investment;

import com.tracky.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Testa o forward-fill do histórico do portefólio e a matemática da projeção. */
@ExtendWith(MockitoExtension.class)
class InvestmentControllerTest {

    @Mock InvestmentRepository repo;
    @Mock PriceService priceService;

    InvestmentController controller;
    User user;

    @BeforeEach
    void setUp() {
        controller = new InvestmentController(repo, priceService);
        user = mock(User.class);
        lenient().when(user.getId()).thenReturn(1L);
    }

    private Investment liveInvestment(String name, String symbol, String quantity) {
        Investment inv = new Investment();
        inv.setUserId(1L);
        inv.setName(name);
        inv.setSymbol(symbol);
        inv.setType(Investment.Type.ETF);
        inv.setQuantity(new BigDecimal(quantity));
        inv.setInitialValue(new BigDecimal("100"));
        return inv;
    }

    // ---------- histórico ----------

    @Test
    void historicoFazForwardFillDeSeriesComDatasEmFalta() {
        Investment a = liveInvestment("A", "AAA", "1");
        Investment b = liveInvestment("B", "BBB", "1");
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(a, b));

        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);
        LocalDate d3 = LocalDate.of(2025, 3, 5);
        // A tem cotação nos 3 dias; B só no primeiro → nos restantes usa o último valor conhecido
        when(priceService.getHistoryEur("AAA", Investment.Type.ETF, "3mo")).thenReturn(List.of(
                new PriceService.PricePoint(d1, new BigDecimal("10")),
                new PriceService.PricePoint(d2, new BigDecimal("11")),
                new PriceService.PricePoint(d3, new BigDecimal("12"))));
        when(priceService.getHistoryEur("BBB", Investment.Type.ETF, "3mo")).thenReturn(List.of(
                new PriceService.PricePoint(d1, new BigDecimal("5"))));

        List<InvestmentController.PortfolioPoint> points = controller.history(user, "3mo");

        assertThat(points).hasSize(3);
        assertThat(points.get(0).value()).isEqualByComparingTo("15"); // 10 + 5
        assertThat(points.get(1).value()).isEqualByComparingTo("16"); // 11 + 5 (forward-fill)
        assertThat(points.get(2).value()).isEqualByComparingTo("17"); // 12 + 5 (forward-fill)
    }

    @Test
    void investimentoManualEntraComoValorConstanteNoHistorico() {
        Investment live = liveInvestment("A", "AAA", "2");
        Investment manual = new Investment();
        manual.setUserId(1L);
        manual.setName("PPR");
        manual.setType(Investment.Type.OTHER);
        manual.setFallbackValue(new BigDecimal("1000"));
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(live, manual));

        LocalDate d1 = LocalDate.of(2025, 3, 3);
        LocalDate d2 = LocalDate.of(2025, 3, 4);
        when(priceService.getHistoryEur("AAA", Investment.Type.ETF, "3mo")).thenReturn(List.of(
                new PriceService.PricePoint(d1, new BigDecimal("10")),
                new PriceService.PricePoint(d2, new BigDecimal("20"))));

        List<InvestmentController.PortfolioPoint> points = controller.history(user, "3mo");

        assertThat(points).hasSize(2);
        assertThat(points.get(0).value()).isEqualByComparingTo("1020"); // 2×10 + 1000
        assertThat(points.get(1).value()).isEqualByComparingTo("1040"); // 2×20 + 1000
    }

    @Test
    void semCotacoesOHistoricoEVazio() {
        Investment manual = new Investment();
        manual.setUserId(1L);
        manual.setName("PPR");
        manual.setType(Investment.Type.OTHER);
        manual.setFallbackValue(new BigDecimal("1000"));
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(manual));

        assertThat(controller.history(user, "3mo")).isEmpty();
    }

    // ---------- projeção ----------

    @Test
    void projecaoCenarioZeroPorCentoAcumulaApenasContribuicoes() {
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        var resp = controller.projection(user, 12, new BigDecimal("100"), null, null);

        assertThat(resp.startValue()).isEqualByComparingTo("0");
        assertThat(resp.months()).isEqualTo(12);
        var invested = resp.scenarios().stream()
                .filter(s -> s.id().equals("investido")).findFirst().orElseThrow();
        assertThat(invested.finalValue()).isEqualByComparingTo("1200"); // 12 × 100€
        assertThat(invested.points()).hasSize(13); // mês 0 + 12
    }

    @Test
    void projecaoParteDoValorAtualDoPortefolio() {
        Investment manual = new Investment();
        manual.setUserId(1L);
        manual.setName("PPR");
        manual.setType(Investment.Type.OTHER);
        manual.setFallbackValue(new BigDecimal("500"));
        manual.setInitialValue(new BigDecimal("500"));
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(manual));
        lenient().when(priceService.getPriceEur(any(), any())).thenReturn(Optional.empty());

        var resp = controller.projection(user, 6, BigDecimal.ZERO, null, null);

        assertThat(resp.startValue()).isEqualByComparingTo("500");
        var invested = resp.scenarios().stream()
                .filter(s -> s.id().equals("investido")).findFirst().orElseThrow();
        assertThat(invested.finalValue()).isEqualByComparingTo("500"); // 0% sem contribuições
    }

    @Test
    void taxaPersonalizadaAcrescentaCenarioEEClampada() {
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        var resp = controller.projection(user, 12, BigDecimal.ZERO, null, new BigDecimal("500"));

        assertThat(resp.scenarios()).hasSize(5);
        var custom = resp.scenarios().stream()
                .filter(s -> s.id().equals("custom")).findFirst().orElseThrow();
        assertThat(custom.annualRatePercent()).isEqualTo(100.0); // clamp a 100%
    }

    @Test
    void horizonteEClampadoEntre1E600Meses() {
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        assertThat(controller.projection(user, 0, BigDecimal.ZERO, null, null).months()).isEqualTo(1);
        assertThat(controller.projection(user, 9999, BigDecimal.ZERO, null, null).months()).isEqualTo(600);
    }

    // ---------- lista/summary ----------

    @Test
    void resumoSomaInvestidoEAtualComPrecoLive() {
        Investment inv = liveInvestment("ETF", "AAA", "10");
        inv.setInitialValue(new BigDecimal("100"));
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));
        when(priceService.getPriceEur("AAA", Investment.Type.ETF)).thenReturn(Optional.of(new BigDecimal("12")));

        var resp = controller.list(user);

        assertThat(resp.summary().totalInvested()).isEqualByComparingTo("100");
        assertThat(resp.summary().totalCurrent()).isEqualByComparingTo("120"); // 10 × 12€
        assertThat(resp.summary().totalGain()).isEqualByComparingTo("20");
        assertThat(resp.summary().totalGainPercent()).isEqualByComparingTo("20.00");
        assertThat(resp.investments().get(0).live()).isTrue();
    }

    @Test
    void semPrecoLiveUsaOFallback() {
        Investment inv = liveInvestment("ETF", "AAA", "10");
        inv.setFallbackValue(new BigDecimal("110"));
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));
        when(priceService.getPriceEur("AAA", Investment.Type.ETF)).thenReturn(Optional.empty());

        var resp = controller.list(user);

        assertThat(resp.investments().get(0).live()).isFalse();
        assertThat(resp.summary().totalCurrent()).isEqualByComparingTo("110");
    }

    @Test
    void refreshLimpaACacheDosAtivosDoUtilizador() {
        Investment inv = liveInvestment("ETF", "AAA", "10");
        inv.setFallbackValue(new BigDecimal("100")); // sem preço live o enrich usa o fallback
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));
        when(priceService.getPriceEur(anyString(), any())).thenReturn(Optional.empty());

        controller.refresh(user);

        org.mockito.Mockito.verify(priceService).evictPrice("AAA", Investment.Type.ETF);
    }
}
