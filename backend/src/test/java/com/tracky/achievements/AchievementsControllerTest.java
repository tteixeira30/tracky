package com.tracky.achievements;

import com.tracky.auth.User;
import com.tracky.calendar.CalendarEventRepository;
import com.tracky.goal.GoalController;
import com.tracky.income.IncomeController;
import com.tracky.investment.Investment;
import com.tracky.investment.InvestmentController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Testa os thresholds das conquistas e o cálculo de pontos/nível. */
@ExtendWith(MockitoExtension.class)
class AchievementsControllerTest {

    @Mock IncomeController incomeController;
    @Mock InvestmentController investmentController;
    @Mock GoalController goalController;
    @Mock CalendarEventRepository calendarRepo;

    AchievementsController controller;
    User user;

    @BeforeEach
    void setUp() {
        controller = new AchievementsController(incomeController, investmentController,
                goalController, calendarRepo);
        user = mock(User.class);
        lenient().when(user.getId()).thenReturn(1L);
    }

    private InvestmentController.InvestmentDto investment(Investment.Type type, String value,
                                                          String monthlyContribution) {
        return new InvestmentController.InvestmentDto(1L, "Ativo", "SYM", type,
                new BigDecimal(value), null, null, new BigDecimal(value),
                BigDecimal.ZERO, BigDecimal.ZERO, false,
                monthlyContribution == null ? null : new BigDecimal(monthlyContribution), 1);
    }

    private void stubPortfolio(String invested, String current, String gainPct,
                               List<InvestmentController.InvestmentDto> invs) {
        var summary = new InvestmentController.Summary(new BigDecimal(invested),
                new BigDecimal(current), new BigDecimal(current).subtract(new BigDecimal(invested)),
                new BigDecimal(gainPct));
        when(investmentController.list(user))
                .thenReturn(new InvestmentController.PortfolioResponse(summary, invs));
    }

    private GoalController.GoalDto goal(String target, String saved, String progress, boolean auto) {
        return new GoalController.GoalDto(1L, "Objetivo", new BigDecimal(target), BigDecimal.TEN,
                new BigDecimal(saved), new BigDecimal(progress), null, null, auto, 1);
    }

    private void stubIncome(List<String> months, String income, String totalPct) {
        when(incomeController.get(org.mockito.ArgumentMatchers.eq(user), isNull()))
                .thenReturn(new IncomeController.IncomeResponse("2025-01", true,
                        new BigDecimal(income), List.of(), BigDecimal.ZERO,
                        totalPct == null ? null : new BigDecimal(totalPct),
                        BigDecimal.ZERO, months, null));
    }

    @Test
    void semDadosNaoHaConquistasENivelE1() {
        stubPortfolio("0", "0", "0", List.of());
        when(goalController.list(user)).thenReturn(List.of());
        stubIncome(List.of(), "0", null);
        when(calendarRepo.countByUserId(1L)).thenReturn(0L);
        when(user.getCurrentBalance()).thenReturn(null);

        var resp = controller.get(user);

        assertThat(resp.unlocked()).isZero();
        assertThat(resp.points()).isZero();
        assertThat(resp.level()).isEqualTo(1);
        assertThat(resp.levelName()).isEqualTo("Iniciante");
        assertThat(resp.percentUnlocked()).isZero();
        assertThat(resp.achievements()).isNotEmpty();
        assertThat(resp.total()).isEqualTo(resp.achievements().size());
    }

    @Test
    void investimentosDesbloqueiamConquistasDeInvestimento() {
        stubPortfolio("12000", "13200", "10", List.of(
                investment(Investment.Type.ETF, "10000", "100"),
                investment(Investment.Type.CRYPTO, "2000", null)));
        when(goalController.list(user)).thenReturn(List.of());
        stubIncome(List.of(), "0", null);
        when(calendarRepo.countByUserId(1L)).thenReturn(0L);
        when(user.getCurrentBalance()).thenReturn(null);

        var resp = controller.get(user);
        var byId = resp.achievements().stream()
                .collect(java.util.stream.Collectors.toMap(AchievementsController.Achievement::id, a -> a));

        assertThat(byId.get("inv-first").unlocked()).isTrue();
        assertThat(byId.get("inv-1k").unlocked()).isTrue();
        assertThat(byId.get("inv-10k").unlocked()).isTrue();      // 12.000€ ≥ 10.000€
        assertThat(byId.get("inv-50k").unlocked()).isFalse();
        assertThat(byId.get("inv-50k").progress()).isEqualTo(24); // 12k/50k
        assertThat(byId.get("inv-diversified").unlocked()).isTrue(); // ETF + CRYPTO
        assertThat(byId.get("inv-crypto").unlocked()).isTrue();
        assertThat(byId.get("inv-reinforce").unlocked()).isTrue();   // reforço de 100€
        assertThat(byId.get("ret-positive").unlocked()).isTrue();    // +10%
        assertThat(byId.get("ret-10").unlocked()).isTrue();
        assertThat(byId.get("ret-25").unlocked()).isFalse();
    }

    @Test
    void objetivosEConsistenciaDesbloqueiamConquistas() {
        stubPortfolio("0", "0", "0", List.of());
        when(goalController.list(user)).thenReturn(List.of(
                goal("1000", "1000", "100", true),
                goal("5000", "2500", "50", false)));
        stubIncome(List.of("2024-11", "2024-12", "2025-01"), "2000", "100");
        when(calendarRepo.countByUserId(1L)).thenReturn(2L);
        when(user.getCurrentBalance()).thenReturn(new BigDecimal("1500"));

        var resp = controller.get(user);
        var byId = resp.achievements().stream()
                .collect(java.util.stream.Collectors.toMap(AchievementsController.Achievement::id, a -> a));

        assertThat(byId.get("sav-first-goal").unlocked()).isTrue();
        assertThat(byId.get("sav-1k").unlocked()).isTrue();          // 3.500€ poupados
        assertThat(byId.get("sav-auto").unlocked()).isTrue();
        assertThat(byId.get("goal-1").unlocked()).isTrue();          // 1 objetivo a 100%
        assertThat(byId.get("goal-half").unlocked()).isTrue();       // 50% de progresso
        assertThat(byId.get("con-3m").unlocked()).isTrue();          // 3 meses
        assertThat(byId.get("con-6m").unlocked()).isFalse();
        assertThat(byId.get("con-full").unlocked()).isTrue();        // 100% distribuído
        assertThat(byId.get("plan-balance").unlocked()).isTrue();
        assertThat(byId.get("plan-event1").unlocked()).isTrue();
        assertThat(byId.get("plan-event5").unlocked()).isFalse();
    }

    @Test
    void pontosENivelSaoCoerentesComAsConquistasDesbloqueadas() {
        stubPortfolio("0", "0", "0", List.of());
        when(goalController.list(user)).thenReturn(List.of());
        stubIncome(List.of("2025-01"), "1000", "50");
        when(calendarRepo.countByUserId(1L)).thenReturn(0L);
        when(user.getCurrentBalance()).thenReturn(null);

        var resp = controller.get(user);

        int expectedPoints = resp.achievements().stream()
                .filter(AchievementsController.Achievement::unlocked)
                .mapToInt(AchievementsController.Achievement::points).sum();
        assertThat(resp.points()).isEqualTo(expectedPoints);
        assertThat(resp.level()).isEqualTo(1 + expectedPoints / 60);
        assertThat(resp.pointsIntoLevel()).isEqualTo(expectedPoints % 60);
    }
}
