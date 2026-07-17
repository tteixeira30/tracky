package com.tracky.contribution;

import com.tracky.auth.User;
import com.tracky.auth.UserRepository;
import com.tracky.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testa o scheduler: aplica depósitos a todos os utilizadores e tolera falhas. */
@ExtendWith(MockitoExtension.class)
class ContributionSchedulerTest {

    @Mock ContributionService service;
    @Mock UserRepository userRepository;

    private User user(long id) {
        User u = new User();
        TestSupport.setId(u, id);
        return u;
    }

    @Test
    void arranqueAplicaDepositosDeTodosOsUtilizadores() {
        var scheduler = new ContributionScheduler(service, userRepository);
        when(userRepository.findAll()).thenReturn(List.of(user(1L), user(2L)));
        when(service.apply(eq(1L), eq("all"), eq(false)))
                .thenReturn(new ContributionService.ApplyResult(
                        List.of(new ContributionService.AppliedItem("goal", "X", 1, BigDecimal.TEN)),
                        BigDecimal.TEN));
        when(service.apply(eq(2L), eq("all"), eq(false)))
                .thenReturn(new ContributionService.ApplyResult(List.of(), BigDecimal.ZERO));

        scheduler.onStartup();

        verify(service).apply(1L, "all", false);
        verify(service).apply(2L, "all", false);
    }

    @Test
    void falhaNumUtilizadorNaoImpedeOsRestantes() {
        var scheduler = new ContributionScheduler(service, userRepository);
        when(userRepository.findAll()).thenReturn(List.of(user(1L), user(2L)));
        when(service.apply(eq(1L), eq("all"), eq(false))).thenThrow(new RuntimeException("boom"));
        when(service.apply(eq(2L), eq("all"), eq(false)))
                .thenReturn(new ContributionService.ApplyResult(List.of(), BigDecimal.ZERO));

        scheduler.daily(); // não deve propagar a exceção

        verify(service).apply(2L, "all", false); // o segundo é processado à mesma
    }
}
