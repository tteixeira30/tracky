package com.tracky.contribution;

import com.tracky.goal.Goal;
import com.tracky.goal.GoalRepository;
import com.tracky.investment.Investment;
import com.tracky.investment.InvestmentRepository;
import com.tracky.investment.PriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testa o catch-up de depósitos mensais (objetivos e investimentos). */
@ExtendWith(MockitoExtension.class)
class ContributionServiceTest {

    @Mock GoalRepository goalRepo;
    @Mock InvestmentRepository investmentRepo;
    @Mock PriceService priceService;

    ContributionService service;

    @BeforeEach
    void setUp() {
        service = new ContributionService(goalRepo, investmentRepo, priceService);
        lenient().when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
        lenient().when(investmentRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
    }

    /** Cria um serviço com "hoje" fixado numa data concreta. */
    private ContributionService serviceAt(LocalDate today) {
        ZoneId zone = ZoneId.of("Europe/Lisbon");
        Clock fixed = Clock.fixed(today.atStartOfDay(zone).toInstant(), zone);
        return new ContributionService(goalRepo, investmentRepo, priceService, fixed);
    }

    private Goal autoGoal(String monthly, String saved, String lastAppliedMonth) {
        Goal g = new Goal();
        g.setUserId(1L);
        g.setName("Objetivo");
        g.setTargetAmount(new BigDecimal("10000"));
        g.setMonthlyAllocation(new BigDecimal(monthly));
        g.setSavedAmount(new BigDecimal(saved));
        g.setAutoDeposit(true);
        g.setLastAppliedMonth(lastAppliedMonth);
        return g;
    }

    @Test
    void objetivoEmDiaNaoRecebeDeposito() {
        Goal g = autoGoal("100", "500", YearMonth.now().toString());
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        var result = service.apply(1L, "all", false);

        assertThat(result.applied()).isEmpty();
        assertThat(g.getSavedAmount()).isEqualByComparingTo("500");
        verify(goalRepo, never()).save(g);
    }

    @Test
    void catchUpAplicaTodosOsMesesEmAtraso() {
        // último aplicado há 3 meses → 3 depósitos de 100€
        String threeMonthsAgo = YearMonth.now().minusMonths(3).toString();
        Goal g = autoGoal("100", "500", threeMonthsAgo);
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        var result = service.apply(1L, "all", false);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).months()).isEqualTo(3);
        assertThat(g.getSavedAmount()).isEqualByComparingTo("800");
        assertThat(g.getLastAppliedMonth()).isEqualTo(YearMonth.now().toString());
        assertThat(result.totalAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void forceAplicaExatamenteUmMesEAvancaOMarcador() {
        String current = YearMonth.now().toString();
        Goal g = autoGoal("100", "500", current);
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        var result = service.apply(1L, "all", true);

        assertThat(result.applied()).hasSize(1);
        assertThat(g.getSavedAmount()).isEqualByComparingTo("600");
        // o marcador avança para o mês seguinte — o scheduler não repete este mês
        assertThat(g.getLastAppliedMonth()).isEqualTo(YearMonth.now().plusMonths(1).toString());
    }

    @Test
    void objetivoSemAutoDepositoOuSemMarcadorEIgnorado() {
        Goal semAuto = autoGoal("100", "0", YearMonth.now().minusMonths(2).toString());
        semAuto.setAutoDeposit(false);
        Goal semMarcador = autoGoal("100", "0", null); // legado: nunca aplicado → não faz catch-up
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(semAuto, semMarcador));

        var result = service.apply(1L, "all", false);

        assertThat(result.applied()).isEmpty();
    }

    @Test
    void scopeGoalsNaoTocaNosInvestimentos() {
        Goal g = autoGoal("100", "0", YearMonth.now().minusMonths(1).toString());
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        var result = service.apply(1L, "goals", false);

        assertThat(result.applied()).hasSize(1);
        verify(investmentRepo, never()).findByUserIdOrderByIdAsc(1L);
    }

    @Test
    void reforcoDeInvestimentoManualSomaAoFallback() {
        Investment inv = new Investment();
        inv.setUserId(1L);
        inv.setName("PPR");
        inv.setType(Investment.Type.OTHER);
        inv.setFallbackValue(new BigDecimal("1000"));
        inv.setInitialValue(new BigDecimal("1000"));
        inv.setMonthlyContribution(new BigDecimal("50"));
        inv.setLastAppliedMonth(YearMonth.now().minusMonths(2).toString());
        when(investmentRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));

        var result = service.apply(1L, "investments", false);

        assertThat(result.applied()).hasSize(1);
        assertThat(inv.getFallbackValue()).isEqualByComparingTo("1100"); // 2 × 50€
        assertThat(inv.getInitialValue()).isEqualByComparingTo("1100");
    }

    @Test
    void reforcoComCotacaoCompraUnidadesAoPrecoAtual() {
        Investment inv = new Investment();
        inv.setUserId(1L);
        inv.setName("ETF");
        inv.setSymbol("VWCE.DE");
        inv.setType(Investment.Type.ETF);
        inv.setQuantity(new BigDecimal("10"));
        inv.setInitialValue(new BigDecimal("1000"));
        inv.setMonthlyContribution(new BigDecimal("100"));
        inv.setLastAppliedMonth(YearMonth.now().minusMonths(1).toString());
        when(investmentRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));
        when(priceService.getPriceEur("VWCE.DE", Investment.Type.ETF))
                .thenReturn(Optional.of(new BigDecimal("50")));

        service.apply(1L, "investments", false);

        // 100€ / 50€ = 2 unidades novas
        assertThat(inv.getQuantity()).isEqualByComparingTo("12");
        assertThat(inv.getInitialValue()).isEqualByComparingTo("1100");
    }

    // ---------- dia do mês configurável (contributionDay) ----------

    @Test
    void semDiaConfiguradoAplicaNoDia1ComoAntes() {
        // legado: contributionDay null → dia efetivo 1, aplica logo no arranque do mês
        Goal g = autoGoal("100", "500", "2025-05");
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        var result = serviceAt(LocalDate.of(2025, 6, 1)).apply(1L, "all", false);

        assertThat(result.applied()).hasSize(1);
        assertThat(g.getSavedAmount()).isEqualByComparingTo("600");
        assertThat(g.getLastAppliedMonth()).isEqualTo("2025-06");
    }

    @Test
    void mesCorrenteSoAplicaAPartirDoDiaConfigurado() {
        Goal g = autoGoal("100", "500", "2025-05");
        g.setContributionDay(10);
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        // dia 9: ainda não chegou ao dia do depósito → nada acontece
        var antes = serviceAt(LocalDate.of(2025, 6, 9)).apply(1L, "all", false);
        assertThat(antes.applied()).isEmpty();
        assertThat(g.getSavedAmount()).isEqualByComparingTo("500");
        assertThat(g.getLastAppliedMonth()).isEqualTo("2025-05");

        // dia 10: aplica o mês corrente
        var depois = serviceAt(LocalDate.of(2025, 6, 10)).apply(1L, "all", false);
        assertThat(depois.applied()).hasSize(1);
        assertThat(g.getSavedAmount()).isEqualByComparingTo("600");
        assertThat(g.getLastAppliedMonth()).isEqualTo("2025-06");
    }

    @Test
    void dia31EmMesCurtoAplicaNoUltimoDiaDoMes() {
        Goal g = autoGoal("100", "0", "2025-05");
        g.setContributionDay(31); // junho só tem 30 dias → dia efetivo é 30
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        assertThat(serviceAt(LocalDate.of(2025, 6, 29)).apply(1L, "all", false).applied()).isEmpty();

        var result = serviceAt(LocalDate.of(2025, 6, 30)).apply(1L, "all", false);
        assertThat(result.applied()).hasSize(1);
        assertThat(g.getSavedAmount()).isEqualByComparingTo("100");
    }

    @Test
    void catchUpDeMesesInteirosEmAtrasoIgnoraODia() {
        // último aplicado em março; hoje é 5 de junho com dia configurado 10:
        // abril e maio (meses inteiros em atraso) aplicam-se já; junho só no dia 10
        Goal g = autoGoal("100", "0", "2025-03");
        g.setContributionDay(10);
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        var result = serviceAt(LocalDate.of(2025, 6, 5)).apply(1L, "all", false);

        assertThat(result.applied()).hasSize(1);
        assertThat(result.applied().get(0).months()).isEqualTo(2);
        assertThat(g.getSavedAmount()).isEqualByComparingTo("200");
        assertThat(g.getLastAppliedMonth()).isEqualTo("2025-05"); // junho fica pendente até ao dia 10
    }

    @Test
    void mudarODiaNaoReaplicaMesJaAplicado() {
        // o mês corrente já foi aplicado (ex.: dia era 5); o utilizador muda o dia para 1:
        // não há segunda aplicação — o lastAppliedMonth é que manda
        Goal g = autoGoal("100", "600", "2025-06");
        g.setContributionDay(1);
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));

        var result = serviceAt(LocalDate.of(2025, 6, 20)).apply(1L, "all", false);

        assertThat(result.applied()).isEmpty();
        assertThat(g.getSavedAmount()).isEqualByComparingTo("600");
        verify(goalRepo, never()).save(g);
    }

    @Test
    void diaConfiguradoTambemValeParaInvestimentos() {
        Investment inv = new Investment();
        inv.setUserId(1L);
        inv.setName("PPR");
        inv.setType(Investment.Type.OTHER);
        inv.setFallbackValue(new BigDecimal("1000"));
        inv.setInitialValue(new BigDecimal("1000"));
        inv.setMonthlyContribution(new BigDecimal("50"));
        inv.setContributionDay(15);
        inv.setLastAppliedMonth("2025-05");
        when(investmentRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));

        assertThat(serviceAt(LocalDate.of(2025, 6, 14)).apply(1L, "investments", false).applied()).isEmpty();

        var result = serviceAt(LocalDate.of(2025, 6, 15)).apply(1L, "investments", false);
        assertThat(result.applied()).hasSize(1);
        assertThat(inv.getFallbackValue()).isEqualByComparingTo("1050");
        assertThat(inv.getLastAppliedMonth()).isEqualTo("2025-06");
    }

    @Test
    void semPrecoDisponivelOReforcoFicaPendente() {
        Investment inv = new Investment();
        inv.setUserId(1L);
        inv.setName("ETF");
        inv.setSymbol("VWCE.DE");
        inv.setType(Investment.Type.ETF);
        inv.setQuantity(new BigDecimal("10"));
        inv.setInitialValue(new BigDecimal("1000"));
        inv.setMonthlyContribution(new BigDecimal("100"));
        String lastMonth = YearMonth.now().minusMonths(1).toString();
        inv.setLastAppliedMonth(lastMonth);
        when(investmentRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));
        when(priceService.getPriceEur("VWCE.DE", Investment.Type.ETF)).thenReturn(Optional.empty());

        var result = service.apply(1L, "investments", false);

        // nada aplicado e o marcador não avança — tenta de novo na próxima execução
        assertThat(result.applied()).isEmpty();
        assertThat(inv.getQuantity()).isEqualByComparingTo("10");
        assertThat(inv.getLastAppliedMonth()).isEqualTo(lastMonth);
        verify(investmentRepo, never()).save(inv);
    }
}
