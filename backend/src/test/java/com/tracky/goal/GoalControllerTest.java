package com.tracky.goal;

import com.tracky.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void contribuicaoNegativaNuncaDeixaOSaldoAbaixoDeZero() {
        Goal g = goal("1000", "100", "200");
        when(repo.findByIdAndUserId(5L, 1L)).thenReturn(java.util.Optional.of(g));
        when(repo.save(g)).thenReturn(g);

        var dto = controller.contribute(user, 5L,
                new GoalController.ContributionRequest(new BigDecimal("-500")));

        assertThat(dto.savedAmount()).isEqualByComparingTo("0");
    }
}
