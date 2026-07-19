package com.tracky.expense;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Movimento de uma conta corrente (despesa ou receita), inserido manualmente
 * ou importado de um extrato bancário. Valores em EUR, sempre positivos —
 * o sentido vem de {@link #inflow}.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    public enum Category {
        INCOME, GROCERIES, RESTAURANT, TRANSPORT, HOUSING, SUBSCRIPTION,
        SHOPPING, HEALTH, LEISURE, TRANSFER, OTHER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "account_id")
    private Long accountId;

    private LocalDate txDate;

    @Column(length = 500)
    private String description;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    /** true = entrada de dinheiro; false = saída (despesa). */
    private boolean inflow = false;

    @Enumerated(EnumType.STRING)
    private Category category = Category.OTHER;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public LocalDate getTxDate() { return txDate; }
    public void setTxDate(LocalDate txDate) { this.txDate = txDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public boolean isInflow() { return inflow; }
    public void setInflow(boolean inflow) { this.inflow = inflow; }
    public Category getCategory() { return category == null ? Category.OTHER : category; }
    public void setCategory(Category category) { this.category = category == null ? Category.OTHER : category; }
    public Instant getCreatedAt() { return createdAt; }
}
