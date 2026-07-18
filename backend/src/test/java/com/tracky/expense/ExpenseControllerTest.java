package com.tracky.expense;

import com.tracky.auth.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testa a chave de categorização (categoryKey) e a propagação de categorias a movimentos semelhantes. */
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
