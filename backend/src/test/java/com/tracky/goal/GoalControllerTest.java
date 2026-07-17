package com.tracky.goal;

import com.tracky.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Testa a matemática do GoalDto (progresso, meses restantes, data estimada) via list(). */
@ExtendWith(MockitoExtension.class)
class GoalControllerTest {

    @Mock GoalRepository repo;

    GoalController controller;
    User user;

    @BeforeEach
    void setUp() {
        controller = new GoalController(repo);
        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
    }

    private Goal goal(String target, String saved, String monthly) {
        Goal g = new Goal();
        g.setUserId(1L);
        g.setName("Objetivo");
        g.setTargetAmount(new BigDecimal(target));
        g.setSavedAmount(new BigDecimal(saved));
        g.setMonthlyAllocation(monthly == null ? null : new BigDecimal(monthly));
        return g;
    }

    private GoalController.GoalDto dtoFor(Goal g) {
        when(repo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(g));
        return controller.list(user).get(0);
    }

    @Test
    void progressoEMesesRestantesCalculados() {
        // faltam 700€ a 200€/mês → 4 meses (arredonda para cima)
        var dto = dtoFor(goal("1000", "300", "200"));

        assertThat(dto.progressPercent()).isEqualByComparingTo("30.0");
        assertThat(dto.monthsRemaining()).isEqualTo(4);
        assertThat(dto.estimatedDate()).isEqualTo(LocalDate.now().plusMonths(4));
    }

    @Test
    void objetivoCompletoTemProgressoLimitadoA100EZeroMeses() {
        var dto = dtoFor(goal("1000", "1500", "200"));

        assertThat(dto.progressPercent()).isEqualByComparingTo("100");
        assertThat(dto.monthsRemaining()).isZero();
        assertThat(dto.estimatedDate()).isNull();
    }

    @Test
    void alvoZeroNaoRebentaEDaProgressoZero() {
        var dto = dtoFor(goal("0", "0", "200"));

        assertThat(dto.progressPercent()).isEqualByComparingTo("0");
        // remaining = 0 → objetivo tratado como completo
        assertThat(dto.monthsRemaining()).isZero();
    }

    @Test
    void semAlocacaoMensalNaoHaEstimativa() {
        var dto = dtoFor(goal("1000", "300", null));

        assertThat(dto.monthsRemaining()).isNull();
        assertThat(dto.estimatedDate()).isNull();
    }

    // ---------- dia do depósito automático (contributionDay) ----------

    @Test
    void criarComDiaDeDepositoGuardaEDevolveODia() {
        when(repo.save(any(Goal.class))).thenAnswer(a -> a.getArgument(0));

        var dto = controller.create(user, new GoalController.GoalRequest(
                "Férias", new BigDecimal("1000"), new BigDecimal("100"), null, true, 15));

        assertThat(dto.contributionDay()).isEqualTo(15);
        assertThat(dto.autoDeposit()).isTrue();
    }

    @Test
    void semDiaODtoDevolveODia1PorOmissao() {
        // objetivos antigos (coluna a null) mantêm o comportamento original: dia 1
        var dto = dtoFor(goal("1000", "300", "200"));

        assertThat(dto.contributionDay()).isEqualTo(1);
    }

    @Test
    void diaForaDe1a31ERejeitadoCom400() {
        for (int dia : new int[]{0, 32}) {
            var req = new GoalController.GoalRequest(
                    "Férias", new BigDecimal("1000"), new BigDecimal("100"), null, true, dia);
            assertThatThrownBy(() -> controller.create(user, req))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void mudarODiaNaoMexeNoLastAppliedMonth() {
        // depósito já ativo com o mês corrente aplicado — mudar o dia não reaplica nem desfaz
        Goal g = goal("1000", "600", "100");
        g.setAutoDeposit(true);
        g.setContributionDay(5);
        g.setLastAppliedMonth("2025-06");
        when(repo.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(g));
        when(repo.save(g)).thenReturn(g);

        var dto = controller.update(user, 5L, new GoalController.GoalRequest(
                "Objetivo", new BigDecimal("1000"), new BigDecimal("100"), null, true, 20));

        assertThat(dto.contributionDay()).isEqualTo(20);
        assertThat(g.getLastAppliedMonth()).isEqualTo("2025-06"); // intocado
        assertThat(g.getSavedAmount()).isEqualByComparingTo("600");
    }

    @Test
    void contribuirDelegaNoIncrementoAtomico() {
        // o incremento é atómico (UPDATE condicional) para não perder depósitos
        // concorrentes; o piso a zero vive no próprio UPDATE (ver teste de integração)
        Goal g = goal("1000", "100", "200");
        when(repo.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(g));

        controller.contribute(user, 5L,
                new GoalController.ContributionRequest(new BigDecimal("-500")));

        org.mockito.Mockito.verify(repo).addToSavedAmount(5L, 1L, new BigDecimal("-500"));
    }
}
