package com.tracky.calendar;

import com.tracky.auth.User;
import com.tracky.expense.Account;
import com.tracky.expense.AccountRepository;
import com.tracky.goal.Goal;
import com.tracky.goal.GoalRepository;
import com.tracky.investment.Investment;
import com.tracky.investment.InvestmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testa a geração de ocorrências do calendário (datas mensais/anuais/pontuais
 * e derivados de investimentos/objetivos) através do endpoint público month().
 */
@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

    @Mock CalendarEventRepository eventRepo;
    @Mock InvestmentRepository investmentRepo;
    @Mock GoalRepository goalRepo;
    @Mock AccountRepository accountRepo;

    CalendarController controller;
    User user;

    @BeforeEach
    void setUp() {
        controller = new CalendarController(eventRepo, investmentRepo, goalRepo, accountRepo);
        user = mock(User.class);
        lenient().when(user.getId()).thenReturn(1L);
        lenient().when(investmentRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
        lenient().when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
        lenient().when(accountRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
    }

    private Account account(String name, String balance) {
        Account a = new Account();
        a.setUserId(1L);
        a.setName(name);
        a.setCurrentBalance(balance == null ? null : new BigDecimal(balance));
        return a;
    }

    /** Evento pontual numa data (usado nos testes de previsão para ocorrências deterministas). */
    private CalendarEvent once(String name, LocalDate date, boolean inflow, String amount) {
        CalendarEvent e = new CalendarEvent();
        e.setUserId(1L);
        e.setName(name);
        e.setCategory(CalendarEvent.Category.OTHER);
        e.setInflow(inflow);
        e.setAmount(new BigDecimal(amount));
        e.setFrequency(CalendarEvent.Frequency.ONCE);
        e.setEventDate(date);
        return e;
    }

    private CalendarEvent monthly(String name, int dayOfMonth, boolean inflow, String amount) {
        CalendarEvent e = new CalendarEvent();
        e.setUserId(1L);
        e.setName(name);
        e.setCategory(CalendarEvent.Category.BILL);
        e.setInflow(inflow);
        e.setAmount(new BigDecimal(amount));
        e.setFrequency(CalendarEvent.Frequency.MONTHLY);
        e.setDayOfMonth(dayOfMonth);
        return e;
    }

    @Test
    void eventoMensalNoDia31EAjustadoAoFimDeFevereiroBissexto() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(monthly("Renda", 31, false, "800")));

        var resp = controller.month(user, "2024-02"); // 2024 é bissexto

        assertThat(resp.occurrences()).hasSize(1);
        assertThat(resp.occurrences().get(0).date()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void eventoMensalNoDia31EAjustadoAoFimDeFevereiroNaoBissexto() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(monthly("Renda", 31, false, "800")));

        var resp = controller.month(user, "2025-02");

        assertThat(resp.occurrences()).hasSize(1);
        assertThat(resp.occurrences().get(0).date()).isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    void eventoPontualForaDoMesNaoGeraOcorrencia() {
        CalendarEvent once = new CalendarEvent();
        once.setUserId(1L);
        once.setName("Seguro");
        once.setAmount(new BigDecimal("120"));
        once.setFrequency(CalendarEvent.Frequency.ONCE);
        once.setEventDate(LocalDate.of(2025, 6, 15));
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(once));

        assertThat(controller.month(user, "2025-05").occurrences()).isEmpty();
        assertThat(controller.month(user, "2025-06").occurrences()).hasSize(1);
    }

    @Test
    void eventoAnualEm29DeFevereiroAjustaEmAnosNaoBissextos() {
        CalendarEvent yearly = new CalendarEvent();
        yearly.setUserId(1L);
        yearly.setName("Aniversário conta");
        yearly.setAmount(new BigDecimal("50"));
        yearly.setFrequency(CalendarEvent.Frequency.YEARLY);
        yearly.setEventDate(LocalDate.of(2024, 2, 29));
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(yearly));

        var resp = controller.month(user, "2025-02");

        assertThat(resp.occurrences()).hasSize(1);
        assertThat(resp.occurrences().get(0).date()).isEqualTo(LocalDate.of(2025, 2, 28));
    }

    @Test
    void eventoInativoEIgnorado() {
        CalendarEvent e = monthly("Ginásio", 5, false, "30");
        e.setActive(false);
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(e));

        assertThat(controller.month(user, "2025-03").occurrences()).isEmpty();
    }

    @Test
    void totaisDoMesSomamEntradasESaidas() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(
                monthly("Salário", 1, true, "2000"),
                monthly("Renda", 5, false, "800")));

        var resp = controller.month(user, "2025-03");

        assertThat(resp.inflows()).isEqualByComparingTo("2000");
        assertThat(resp.outflows()).isEqualByComparingTo("800");
        assertThat(resp.net()).isEqualByComparingTo("1200");
    }

    @Test
    void reforcosDeInvestimentosEDepositosDeObjetivosGeramOcorrenciasNoDia1() {
        // sem dia configurado (linhas antigas a null) mantém-se o comportamento original: dia 1
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        Investment inv = new Investment();
        inv.setUserId(1L);
        inv.setName("ETF Mundo");
        inv.setMonthlyContribution(new BigDecimal("100"));
        when(investmentRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));

        Goal goal = new Goal();
        goal.setUserId(1L);
        goal.setName("Férias");
        goal.setAutoDeposit(true);
        goal.setMonthlyAllocation(new BigDecimal("150"));
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(goal));

        var resp = controller.month(user, "2025-03");

        assertThat(resp.occurrences()).hasSize(2);
        assertThat(resp.occurrences()).allMatch(o -> o.date().equals(LocalDate.of(2025, 3, 1)));
        assertThat(resp.occurrences()).extracting("source").containsExactlyInAnyOrder("INVESTMENT", "GOAL");
        assertThat(resp.outflows()).isEqualByComparingTo("250");
    }

    @Test
    void reforcosEDepositosUsamODiaConfiguradoClampadoAoComprimentoDoMes() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        Investment inv = new Investment();
        inv.setUserId(1L);
        inv.setName("ETF Mundo");
        inv.setMonthlyContribution(new BigDecimal("100"));
        inv.setContributionDay(15);
        when(investmentRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(inv));

        Goal goal = new Goal();
        goal.setUserId(1L);
        goal.setName("Férias");
        goal.setAutoDeposit(true);
        goal.setMonthlyAllocation(new BigDecimal("150"));
        goal.setContributionDay(31); // fevereiro de 2025 só tem 28 dias → clampa ao dia 28
        when(goalRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(goal));

        var resp = controller.month(user, "2025-02");

        assertThat(resp.occurrences()).hasSize(2);
        var invOcc = resp.occurrences().stream().filter(o -> o.source().equals("INVESTMENT")).findFirst().orElseThrow();
        var goalOcc = resp.occurrences().stream().filter(o -> o.source().equals("GOAL")).findFirst().orElseThrow();
        assertThat(invOcc.date()).isEqualTo(LocalDate.of(2025, 2, 15));
        assertThat(goalOcc.date()).isEqualTo(LocalDate.of(2025, 2, 28));
    }

    // ---------- previsão de saldo (agora a partir da soma das contas bancárias) ----------

    @Test
    void previsaoSemContasNaoTemSaldoEAcumulaFluxoAPartirDeZero() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(
                once("Compra", LocalDate.now().plusDays(10), false, "100")));
        // accountRepo devolve lista vazia (stub por omissão) → sem saldo definido

        var resp = controller.upcoming(user, 60);

        assertThat(resp.hasBalance()).isFalse();
        assertThat(resp.startingBalance()).isNull();
        assertThat(resp.points()).hasSize(1);
        assertThat(resp.points().get(0).balanceAfter()).isEqualByComparingTo("-100");
        assertThat(resp.endBalance()).isEqualByComparingTo("-100");
    }

    @Test
    void previsaoUsaSomaDosSaldosDefinidosNasContasEIgnoraOsNulos() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(
                once("Compra", LocalDate.now().plusDays(5), false, "200")));
        when(accountRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(
                account("Santander", "1000"),
                account("Revolut", "500"),
                account("Trade Republic", null))); // sem saldo definido → não conta

        var resp = controller.upcoming(user, 60);

        assertThat(resp.hasBalance()).isTrue();
        assertThat(resp.startingBalance()).isEqualByComparingTo("1500");
        assertThat(resp.points()).hasSize(1);
        assertThat(resp.points().get(0).balanceAfter()).isEqualByComparingTo("1300");
        assertThat(resp.endBalance()).isEqualByComparingTo("1300");
    }

    @Test
    void contaSemSaldoDefinidoNaoAtivaPrevisaoDeSaldo() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(accountRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(account("Santander", null)));

        var resp = controller.upcoming(user, 60);

        assertThat(resp.hasBalance()).isFalse();
        assertThat(resp.startingBalance()).isNull();
        assertThat(resp.points()).isEmpty();
        assertThat(resp.endBalance()).isEqualByComparingTo("0");
    }

    @Test
    void saldoAcumulaPorOrdemDeDataComEntradasESaidas() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(
                once("Salário", LocalDate.now().plusDays(5), true, "300"),
                once("Renda", LocalDate.now().plusDays(10), false, "100")));
        when(accountRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(account("Santander", "1000")));

        var resp = controller.upcoming(user, 60);

        assertThat(resp.startingBalance()).isEqualByComparingTo("1000");
        assertThat(resp.points()).hasSize(2);
        assertThat(resp.points().get(0).balanceAfter()).isEqualByComparingTo("1300"); // +300
        assertThat(resp.points().get(1).balanceAfter()).isEqualByComparingTo("1200"); // -100
        assertThat(resp.endBalance()).isEqualByComparingTo("1200");
    }

    @Test
    void saldoNegativoQuandoAsSaidasExcedemOSaldoInicial() {
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(
                once("Compra grande", LocalDate.now().plusDays(7), false, "250")));
        when(accountRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(account("Revolut", "100")));

        var resp = controller.upcoming(user, 60);

        assertThat(resp.hasBalance()).isTrue();
        assertThat(resp.startingBalance()).isEqualByComparingTo("100");
        assertThat(resp.endBalance()).isEqualByComparingTo("-150");
    }
}
