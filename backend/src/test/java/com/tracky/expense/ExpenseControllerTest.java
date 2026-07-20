package com.tracky.expense;

import com.tracky.auth.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa a lógica do ExpenseController: chave de categorização, propagação de
 * categorias a movimentos semelhantes, estatísticas, dedupe de importação,
 * agregação mensal e o scoping das contas ao utilizador.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseControllerTest {

    @Mock AccountRepository accounts;
    @Mock TransactionRepository transactions;
    @Mock CategoryRuleRepository rules;

    @Test
    void categoryKeyIgnoraReferenciasDatasEPontuacao() {
        // referências e datas diferentes → mesma chave (mesmo comerciante)
        assertThat(ExpenseController.categoryKey("COMPRA 1234 CONTINENTE COLOMBO 12/03"))
                .isEqualTo("compra continente colombo");
        assertThat(ExpenseController.categoryKey("COMPRA 5678 CONTINENTE COLOMBO 15/04"))
                .isEqualTo("compra continente colombo");
        // mantém letras acentuadas
        assertThat(ExpenseController.categoryKey("CAFÉ CENTRAL 99")).isEqualTo("café central");
        // descrições só com números/pontuação → chave vazia (não gera regra)
        assertThat(ExpenseController.categoryKey("   ")).isEmpty();
        assertThat(ExpenseController.categoryKey(null)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyToSimilarPropagaCategoriaAMovimentosDoMesmoComercianteComReferenciasDiferentes() {
        ExpenseController controller = new ExpenseController(accounts, transactions, rules);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        Account account = mock(Account.class);
        when(account.getId()).thenReturn(10L);
        when(account.getName()).thenReturn("Santander");
        when(accounts.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rules.findByUserIdAndMatchKey(1L, "compra continente colombo")).thenReturn(Optional.empty());

        // movimentos existentes: dois do mesmo comerciante (referências diferentes) + um não relacionado
        Transaction t1 = tx("COMPRA 1111 CONTINENTE COLOMBO 01/03", Transaction.Category.OTHER);
        Transaction t2 = tx("COMPRA 2222 CONTINENTE COLOMBO 09/03", Transaction.Category.OTHER);
        Transaction netflix = tx("NETFLIX 12/03", Transaction.Category.OTHER);
        when(transactions.findByUserId(1L)).thenReturn(List.of(t1, t2, netflix));

        var req = new ExpenseController.TransactionRequest(10L, LocalDate.of(2026, 3, 20),
                "COMPRA 3333 CONTINENTE COLOMBO 20/03", new BigDecimal("30"), false, "GROCERIES", true);
        controller.create(user, req);

        // guarda a regra com a chave do comerciante (sem referências/datas)
        ArgumentCaptor<CategoryRule> ruleCap = ArgumentCaptor.forClass(CategoryRule.class);
        verify(rules).save(ruleCap.capture());
        assertThat(ruleCap.getValue().getMatchKey()).isEqualTo("compra continente colombo");
        assertThat(ruleCap.getValue().getCategory()).isEqualTo(Transaction.Category.GROCERIES);

        // recategoriza os dois movimentos do mesmo comerciante; o Netflix fica intacto
        ArgumentCaptor<List<Transaction>> savedCap = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(savedCap.capture());
        assertThat(savedCap.getValue()).containsExactlyInAnyOrder(t1, t2);
        assertThat(t1.getCategory()).isEqualTo(Transaction.Category.GROCERIES);
        assertThat(t2.getCategory()).isEqualTo(Transaction.Category.GROCERIES);
        assertThat(netflix.getCategory()).isEqualTo(Transaction.Category.OTHER);
    }

    @Test
    void statsAgregaSaidasPorMesMediaAnualETopCategorias() {
        ExpenseController controller = new ExpenseController(accounts, transactions, rules);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        YearMonth current = YearMonth.now();
        YearMonth prev = current.minusMonths(1);

        // mês atual: 100 (supermercado) + 50 (transportes) de saída, 1500 de entrada
        Transaction a = txOn(current.atDay(3), new BigDecimal("100"), false, Transaction.Category.GROCERIES);
        Transaction b = txOn(current.atDay(10), new BigDecimal("50"), false, Transaction.Category.TRANSPORT);
        Transaction income = txOn(current.atDay(5), new BigDecimal("1500"), true, Transaction.Category.INCOME);
        // mês anterior: 200 de saída (supermercado)
        Transaction prevTx = txOn(prev.atDay(8), new BigDecimal("200"), false, Transaction.Category.GROCERIES);

        when(transactions.findByUserIdAndTxDateBetweenOrderByTxDateDescIdDesc(anyLong(), any(), any()))
                .thenReturn(List.of(a, b, income, prevTx));

        ExpenseController.ExpenseStats stats = controller.stats(user);

        assertThat(stats.months()).hasSize(12);
        assertThat(stats.hasData()).isTrue();
        assertThat(stats.yearOutflows()).isEqualByComparingTo("350");   // 100+50+200
        assertThat(stats.yearInflows()).isEqualByComparingTo("1500");
        assertThat(stats.currentMonthOutflows()).isEqualByComparingTo("150");
        assertThat(stats.prevMonthOutflows()).isEqualByComparingTo("200");
        assertThat(stats.avgMonthlyOutflows()).isEqualByComparingTo("29.17"); // 350/12

        // top categoria: supermercado (100+200=300) à frente de transportes (50); entradas não contam
        assertThat(stats.topCategories().get(0).category()).isEqualTo("GROCERIES");
        assertThat(stats.topCategories().get(0).total()).isEqualByComparingTo("300");

        // o último ponto da série é o mês atual, com as saídas por categoria desse mês
        ExpenseController.MonthStat last = stats.months().get(11);
        assertThat(last.month()).isEqualTo(current.toString());
        assertThat(last.outflows()).isEqualByComparingTo("150");
        assertThat(last.inflows()).isEqualByComparingTo("1500");
        assertThat(last.byCategory()).extracting(ExpenseController.CategoryTotal::category)
                .containsExactly("GROCERIES", "TRANSPORT"); // ordenado por total desc (100, 50)

        // um mês sem movimentos tem categorias vazias (não null)
        assertThat(stats.months().get(0).byCategory()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void importIgnoraDuplicadosPorContagemEAplicaRegrasDeCategoria() {
        ExpenseController controller = new ExpenseController(accounts, transactions, rules);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        Account account = mock(Account.class);
        when(account.getId()).thenReturn(10L);
        when(accounts.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(account));

        LocalDate day = LocalDate.of(2026, 7, 5);
        // já existe UM movimento igual na conta (mesma data, valor, sentido e descrição)
        Transaction existing = importedTx(day, new BigDecimal("45.30"), false, "Continente");
        when(transactions.findByUserIdAndAccountIdAndTxDateBetween(1L, 10L, day, day))
                .thenReturn(List.of(existing));
        // regra aprendida: qualquer "netflix" é subscrição, independentemente da categoria do ficheiro
        CategoryRule rule = mock(CategoryRule.class);
        when(rule.getMatchKey()).thenReturn("netflix");
        when(rule.getCategory()).thenReturn(Transaction.Category.SUBSCRIPTION);
        when(rules.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(rule));

        var rows = List.of(
                new ExpenseController.ImportRow(day, "Continente", new BigDecimal("45.30"), false, "GROCERIES"), // dup do existente → ignorado
                new ExpenseController.ImportRow(day, "Continente", new BigDecimal("45.30"), false, "GROCERIES"), // 2ª ocorrência → importada
                new ExpenseController.ImportRow(day, "Netflix", new BigDecimal("12.99"), false, "OTHER"));       // nova → importada (regra sobrepõe → SUBSCRIPTION)
        ExpenseController.ImportResult result = controller.importRows(user, new ExpenseController.ImportRequest(10L, rows));

        // multiset: o existente absorve só UMA ocorrência do ficheiro
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);

        ArgumentCaptor<List<Transaction>> batchCap = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(batchCap.capture());
        List<Transaction> saved = batchCap.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(Transaction::getDescription).containsExactly("Continente", "Netflix");
        assertThat(saved).extracting(Transaction::getCategory)
                .containsExactly(Transaction.Category.GROCERIES, Transaction.Category.SUBSCRIPTION);
        assertThat(saved.get(0).getAmount()).isEqualByComparingTo("45.30");
    }

    @Test
    void monthAgregaEntradasSaidasNetECategoriasSoDeSaida() {
        ExpenseController controller = new ExpenseController(accounts, transactions, rules);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);

        Account account = mock(Account.class);
        when(account.getId()).thenReturn(10L);
        when(account.getName()).thenReturn("Santander");
        when(account.getCurrentBalance()).thenReturn(new BigDecimal("1000.00"));
        when(accounts.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(account));
        when(transactions.countByUserIdAndAccountId(1L, 10L)).thenReturn(3L);

        Transaction out1 = txOn(LocalDate.of(2026, 7, 3), new BigDecimal("100"), false, Transaction.Category.GROCERIES);
        Transaction out2 = txOn(LocalDate.of(2026, 7, 10), new BigDecimal("50"), false, Transaction.Category.TRANSPORT);
        Transaction in1 = txOn(LocalDate.of(2026, 7, 5), new BigDecimal("1500"), true, Transaction.Category.INCOME);
        when(transactions.findByUserIdAndTxDateBetweenOrderByTxDateDescIdDesc(
                1L, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(out1, out2, in1));

        ExpenseController.MonthSummary summary = controller.month(user, "2026-07", null);

        assertThat(summary.month()).isEqualTo("2026-07");
        assertThat(summary.inflows()).isEqualByComparingTo("1500");
        assertThat(summary.outflows()).isEqualByComparingTo("150"); // 100 + 50
        assertThat(summary.net()).isEqualByComparingTo("1350");      // 1500 − 150
        assertThat(summary.transactions()).hasSize(3);

        // byCategory só conta saídas, ordenadas por total desc; entradas (INCOME) não entram
        assertThat(summary.byCategory()).extracting(ExpenseController.CategoryTotal::category)
                .containsExactly("GROCERIES", "TRANSPORT");
        assertThat(summary.accounts()).singleElement()
                .satisfies(a -> {
                    assertThat(a.name()).isEqualTo("Santander");
                    assertThat(a.transactionCount()).isEqualTo(3L);
                    assertThat(a.currentBalance()).isEqualByComparingTo("1000.00");
                });
    }

    @Test
    void criarMovimentoNumaContaDeOutroUtilizadorFalhaComBadRequest() {
        ExpenseController controller = new ExpenseController(accounts, transactions, rules);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        // a conta 99 não pertence a este utilizador
        when(accounts.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        var req = new ExpenseController.TransactionRequest(99L, LocalDate.of(2026, 7, 1),
                "Tentativa", new BigDecimal("10"), false, "OTHER", false);

        assertThatThrownBy(() -> controller.create(user, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private Transaction importedTx(LocalDate date, BigDecimal amount, boolean inflow, String description) {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setAccountId(10L);
        t.setTxDate(date);
        t.setDescription(description);
        t.setAmount(amount);
        t.setInflow(inflow);
        t.setCategory(Transaction.Category.OTHER);
        return t;
    }

    private Transaction txOn(LocalDate date, BigDecimal amount, boolean inflow, Transaction.Category category) {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setAccountId(10L);
        t.setTxDate(date);
        t.setDescription("mov");
        t.setAmount(amount);
        t.setInflow(inflow);
        t.setCategory(category);
        return t;
    }

    private Transaction tx(String description, Transaction.Category category) {
        Transaction t = new Transaction();
        t.setUserId(1L);
        t.setAccountId(10L);
        t.setTxDate(LocalDate.of(2026, 3, 1));
        t.setDescription(description);
        t.setAmount(new BigDecimal("10"));
        t.setInflow(false);
        t.setCategory(category);
        return t;
    }
}
