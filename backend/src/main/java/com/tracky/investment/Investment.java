package com.tracky.investment;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "investments")
public class Investment {

    public enum Type { STOCK, ETF, CRYPTO, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String name;

    /** Yahoo Finance ticker (ex: AAPL, VWCE.DE) ou símbolo de cripto (ex: BTC). Null para OTHER. */
    private String symbol;

    @Enumerated(EnumType.STRING)
    private Type type;

    /** Valor investido originalmente (calculado a partir do valor atual e % de ganho). */
    @Column(precision = 19, scale = 4)
    private BigDecimal initialValue;

    /** Unidades detidas (calculado com o preço de mercado no momento do registo). Null se sem preço live. */
    @Column(precision = 19, scale = 8)
    private BigDecimal quantity;

    /** Último valor conhecido — usado quando não há preço em tempo real. */
    @Column(precision = 19, scale = 4)
    private BigDecimal fallbackValue;

    /** Reforço mensal automático em EUR (null quando não há depósito recorrente). */
    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyContribution;

    /** Último mês (formato AAAA-MM) em que o reforço mensal foi aplicado. */
    private String lastAppliedMonth;

    /**
     * Dia do mês (1..31) em que o reforço mensal é aplicado. Null (linhas antigas)
     * equivale a 1 — o comportamento original de aplicar no arranque do mês.
     */
    private Integer contributionDay;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public BigDecimal getInitialValue() { return initialValue; }
    public void setInitialValue(BigDecimal initialValue) { this.initialValue = initialValue; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getFallbackValue() { return fallbackValue; }
    public void setFallbackValue(BigDecimal fallbackValue) { this.fallbackValue = fallbackValue; }
    public BigDecimal getMonthlyContribution() { return monthlyContribution; }
    public void setMonthlyContribution(BigDecimal monthlyContribution) { this.monthlyContribution = monthlyContribution; }
    public String getLastAppliedMonth() { return lastAppliedMonth; }
    public void setLastAppliedMonth(String lastAppliedMonth) { this.lastAppliedMonth = lastAppliedMonth; }
    /** Dia efetivo do reforço, sempre em [1..31]; null (legado) devolve 1. */
    public int getContributionDay() {
        return contributionDay == null ? 1 : Math.min(31, Math.max(1, contributionDay));
    }
    public void setContributionDay(Integer contributionDay) {
        this.contributionDay = contributionDay == null ? null : Math.min(31, Math.max(1, contributionDay));
    }
    public Instant getCreatedAt() { return createdAt; }
}
