package com.tracky.income;

import com.tracky.TestSupport;
import com.tracky.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testa a agregação do buildResponse (montantes, percentagens efetivas,
 * não alocado) via get() com um mês passado (sem copy-forward), e as
 * validações de categorias. Vive neste pacote porque os repositórios do
 * IncomeController são interfaces package-private.
 */
@ExtendWith(MockitoExtension.class)
class IncomeControllerTest {

    // mês passado fixo: nunca coincide com o mês atual, logo não há copy-forward
    private static final String MONTH = "2020-01";

    @Mock IncomeSettingsRepository incomeRepo;
    @Mock AllocationRepository allocationRepo;
    @Mock AllocationItemRepository itemRepo;

    IncomeController controller;
    User user;

    @BeforeEach
    void setUp() {
        controller = new IncomeController(incomeRepo, allocationRepo, itemRepo);
        user = mock(User.class);
        lenient().when(user.getId()).thenReturn(1L);
        // sem linhas legadas por migrar
        lenient().when(incomeRepo.findByUserIdAndMonthIsNull(1L)).thenReturn(List.of());
        lenient().when(allocationRepo.findByUserIdAndMonthIsNull(1L)).thenReturn(List.of());
        lenient().when(incomeRepo.findByUserIdOrderByMonthAsc(1L)).thenReturn(List.of());
        lenient().when(allocationRepo.findByUserIdAndMonthOrderByIdAsc(1L, MONTH)).thenReturn(List.of());
    }

    private IncomeSettings settings(String income) {
        IncomeSettings s = new IncomeSettings();
        s.setUserId(1L);
        s.setMonth(MONTH);
        s.setMonthlyIncome(new BigDecimal(income));
        return s;
    }

    private Allocation allocation(Long id, String name, String percentage, String fixed) {
        Allocation a = new Allocation();
        TestSupport.setId(a, id);
        a.setUserId(1L);
        a.setMonth(MONTH);
        a.setName(name);
        a.setPercentage(percentage == null ? null : new BigDecimal(percentage));
        a.setFixedAmount(fixed == null ? null : new BigDecimal(fixed));
        return a;
    }

    @Test
    void categoriasPorPercentagemEValorFixoSomamCorretamente() {
        when(incomeRepo.findByUserIdAndMonthOrderByIdAsc(1L, MONTH)).thenReturn(List.of(settings("2000")));
        when(allocationRepo.findByUserIdAndMonthOrderByIdAsc(1L, MONTH)).thenReturn(List.of(
                allocation(10L, "Poupança", "50", null),
                allocation(11L, "Subscrições", null, "300")));
        when(itemRepo.findByUserIdAndAllocationIdInOrderByIdAsc(any(), anyList())).thenReturn(List.of());

        var resp = controller.get(user, MONTH);

        assertThat(resp.monthlyIncome()).isEqualByComparingTo("2000");
        var poupanca = resp.allocations().get(0);
        assertThat(poupanca.amount()).isEqualByComparingTo("1000.00");   // 50% de 2000€
        assertThat(poupanca.effectivePercentage()).isEqualByComparingTo("50.0");
        var subs = resp.allocations().get(1);
        assertThat(subs.amount()).isEqualByComparingTo("300");
        assertThat(subs.effectivePercentage()).isEqualByComparingTo("15.0"); // 300/2000
        assertThat(resp.totalAllocated()).isEqualByComparingTo("1300.00");
        assertThat(resp.totalPercentage()).isEqualByComparingTo("65.0");
        assertThat(resp.unallocated()).isEqualByComparingTo("700.00");
    }

    @Test
    void itensDaCategoriaSaoAssociadosESomados() {
        when(incomeRepo.findByUserIdAndMonthOrderByIdAsc(1L, MONTH)).thenReturn(List.of(settings("1000")));
        when(allocationRepo.findByUserIdAndMonthOrderByIdAsc(1L, MONTH))
                .thenReturn(List.of(allocation(10L, "Subscrições", null, "50")));

        AllocationItem netflix = new AllocationItem();
        netflix.setUserId(1L);
        netflix.setAllocationId(10L);
        netflix.setName("Netflix");
        netflix.setAmount(new BigDecimal("15.99"));
        AllocationItem hbo = new AllocationItem();
        hbo.setUserId(1L);
        hbo.setAllocationId(10L);
        hbo.setName("HBO");
        hbo.setAmount(new BigDecimal("9.99"));
        when(itemRepo.findByUserIdAndAllocationIdInOrderByIdAsc(any(), anyList()))
                .thenReturn(List.of(netflix, hbo));

        var resp = controller.get(user, MONTH);

        var subs = resp.allocations().get(0);
        assertThat(subs.items()).hasSize(2);
        assertThat(subs.itemsTotal()).isEqualByComparingTo("25.98");
    }

    @Test
    void rendimentoZeroNaoRebentaNasPercentagens() {
        when(incomeRepo.findByUserIdAndMonthOrderByIdAsc(1L, MONTH)).thenReturn(List.of(settings("0")));
        when(allocationRepo.findByUserIdAndMonthOrderByIdAsc(1L, MONTH))
                .thenReturn(List.of(allocation(10L, "Poupança", "50", null)));
        when(itemRepo.findByUserIdAndAllocationIdInOrderByIdAsc(any(), anyList())).thenReturn(List.of());

        var resp = controller.get(user, MONTH);

        // sem rendimento, a % efetiva cai para a % nominal
        assertThat(resp.allocations().get(0).effectivePercentage()).isEqualByComparingTo("50");
        assertThat(resp.totalPercentage()).isEqualByComparingTo("50");
    }

    @Test
    void linhasDuplicadasDeRendimentoSaoAutoLimpas() {
        IncomeSettings first = settings("2000");
        IncomeSettings dup = settings("999");
        when(incomeRepo.findByUserIdAndMonthOrderByIdAsc(1L, MONTH)).thenReturn(List.of(first, dup));

        var resp = controller.get(user, MONTH);

        assertThat(resp.monthlyIncome()).isEqualByComparingTo("2000"); // fica a primeira
        org.mockito.Mockito.verify(incomeRepo).deleteAll(List.of(dup));
    }

    @Test
    void mesInvalidoDevolve400() {
        assertThatThrownBy(() -> controller.get(user, "2020-13"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("AAAA-MM");
    }

    @Test
    void categoriaComPercentagemEValorFixoEmSimultaneoERejeitada() {
        var req = new IncomeController.AllocationRequest("Mista",
                new BigDecimal("10"), new BigDecimal("100"), null);

        assertThatThrownBy(() -> controller.addAllocation(user, MONTH, req))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void corInvalidaERejeitada() {
        var req = new IncomeController.AllocationRequest("Poupança",
                new BigDecimal("10"), null, "vermelho");

        assertThatThrownBy(() -> controller.addAllocation(user, MONTH, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("#RRGGBB");
    }
}
