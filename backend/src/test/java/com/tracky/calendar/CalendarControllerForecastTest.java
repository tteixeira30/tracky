package com.tracky.calendar;

import com.tracky.auth.User;
import com.tracky.expense.Account;
import com.tracky.expense.AccountRepository;
import com.tracky.goal.GoalRepository;
import com.tracky.investment.InvestmentRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre a previsão de saldo (upcoming/setBalance), o CRUD de eventos e as
 * validações do apply() e do parseMonth — complementa o CalendarControllerTest.
 */
@ExtendWith(MockitoExtension.class)
class CalendarControllerForecastTest {

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
    }

    private CalendarEvent monthly(String name, int day, boolean inflow, String amount) {
        CalendarEvent e = new CalendarEvent();
        e.setUserId(1L);
        e.setName(name);
        e.setCategory(CalendarEvent.Category.BILL);
        e.setInflow(inflow);
        e.setAmount(new BigDecimal(amount));
        e.setFrequency(CalendarEvent.Frequency.MONTHLY);
        e.setDayOfMonth(day);
        e.setActive(true);
        return e;
    }

    private CalendarController.EventRequest req(CalendarEvent.Frequency freq, Integer day, LocalDate date) {
        return new CalendarController.EventRequest("Salário", CalendarEvent.Category.INCOME, true,
                new BigDecimal("100"), freq, day, date, null);
    }

    private Account account(String balance) {
        Account a = new Account();
        a.setUserId(1L);
        a.setName("Conta");
        a.setCurrentBalance(new BigDecimal(balance));
        return a;
    }

    // ---------- upcoming / forecast ----------

    @Test
    void upcomingSemSaldoDefinidoNaoTemSaldoInicial() {
        // nenhuma conta com saldo definido → sem previsão de saldo
        when(accountRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        var resp = controller.upcoming(user, 60);

        assertThat(resp.hasBalance()).isFalse();
        assertThat(resp.startingBalance()).isNull();
        assertThat(resp.endBalance()).isEqualByComparingTo("0");
    }

    @Test
    void upcomingAcumulaOSaldoAoLongoDasOcorrencias() {
        // saldo inicial = soma dos saldos das contas bancárias (funcionalidade de despesas)
        when(accountRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(account("1000")));
        // um evento mensal de entrada garante pelo menos uma ocorrência no horizonte
        when(eventRepo.findByUserIdOrderByIdAsc(1L))
                .thenReturn(List.of(monthly("Salário", LocalDate.now().getDayOfMonth(), true, "500")));

        var resp = controller.upcoming(user, 60);

        assertThat(resp.hasBalance()).isTrue();
        assertThat(resp.startingBalance()).isEqualByComparingTo("1000");
        assertThat(resp.points()).isNotEmpty();
        // cada ponto traz o saldo corrido; o primeiro soma a entrada ao saldo inicial
        assertThat(resp.points().get(0).balanceAfter()).isEqualByComparingTo("1500");
    }

    @Test
    void upcomingLimitaOHorizonteEntre7E365Dias() {
        when(accountRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(eventRepo.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        assertThat(controller.upcoming(user, 2).days()).isEqualTo(7);     // mínimo
        assertThat(controller.upcoming(user, 999).days()).isEqualTo(365); // máximo
        assertThat(controller.upcoming(user, 90).days()).isEqualTo(90);
    }

    // ---------- CRUD ----------

    @Test
    void createGuardaOEventoMensal() {
        when(eventRepo.save(any(CalendarEvent.class))).thenAnswer(a -> a.getArgument(0));

        var dto = controller.create(user, req(CalendarEvent.Frequency.MONTHLY, 15, null));

        assertThat(dto.dayOfMonth()).isEqualTo(15);
        assertThat(dto.eventDate()).isNull();
        assertThat(dto.active()).isTrue(); // active null → true por omissão
    }

    @Test
    void createEventoPontualGuardaADataENaoODia() {
        when(eventRepo.save(any(CalendarEvent.class))).thenAnswer(a -> a.getArgument(0));

        var dto = controller.create(user, req(CalendarEvent.Frequency.ONCE, null, LocalDate.of(2025, 6, 10)));

        assertThat(dto.eventDate()).isEqualTo(LocalDate.of(2025, 6, 10));
        assertThat(dto.dayOfMonth()).isNull();
    }

    @Test
    void eventoMensalSemDiaValidoERejeitadoCom400() {
        assertThatThrownBy(() -> controller.create(user, req(CalendarEvent.Frequency.MONTHLY, 0, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> controller.create(user, req(CalendarEvent.Frequency.MONTHLY, null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void eventoPontualSemDataERejeitadoCom400() {
        assertThatThrownBy(() -> controller.create(user, req(CalendarEvent.Frequency.ONCE, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateAlteraUmEventoExistente() {
        CalendarEvent existing = monthly("Antigo", 1, false, "50");
        when(eventRepo.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(existing));
        when(eventRepo.save(any(CalendarEvent.class))).thenAnswer(a -> a.getArgument(0));

        var dto = controller.update(user, 5L, req(CalendarEvent.Frequency.MONTHLY, 20, null));

        assertThat(dto.name()).isEqualTo("Salário");
        assertThat(dto.dayOfMonth()).isEqualTo(20);
        assertThat(dto.inflow()).isTrue();
    }

    @Test
    void deleteRemoveQuandoExiste() {
        CalendarEvent existing = monthly("X", 1, false, "10");
        when(eventRepo.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(existing));

        controller.delete(user, 5L);

        verify(eventRepo).delete(existing);
    }

    @Test
    void deleteNaoRebentaQuandoNaoExiste() {
        when(eventRepo.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        controller.delete(user, 9L); // sem exceção

        verify(eventRepo, org.mockito.Mockito.never()).delete(any());
    }

    // ---------- parseMonth ----------

    @Test
    void mesInvalidoNoMonthDevolve400() {
        assertThatThrownBy(() -> controller.month(user, "2020-13"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
