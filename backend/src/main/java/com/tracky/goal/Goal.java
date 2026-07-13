package com.tracky.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String name;

    @Column(precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyAllocation;

    @Column(precision = 19, scale = 2)
    private BigDecimal savedAmount = BigDecimal.ZERO;

    /** Quando ativo, a alocação mensal é depositada automaticamente no dia 1 de cada mês. */
    private Boolean autoDeposit = false;

    /** Último mês (formato AAAA-MM) em que o depósito automático foi aplicado. */
    private String lastAppliedMonth;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getMonthlyAllocation() { return monthlyAllocation; }
    public void setMonthlyAllocation(BigDecimal monthlyAllocation) { this.monthlyAllocation = monthlyAllocation; }
    public BigDecimal getSavedAmount() { return savedAmount; }
    public void setSavedAmount(BigDecimal savedAmount) { this.savedAmount = savedAmount; }
    public boolean isAutoDeposit() { return Boolean.TRUE.equals(autoDeposit); }
    public void setAutoDeposit(Boolean autoDeposit) { this.autoDeposit = autoDeposit; }
    public String getLastAppliedMonth() { return lastAppliedMonth; }
    public void setLastAppliedMonth(String lastAppliedMonth) { this.lastAppliedMonth = lastAppliedMonth; }
    public Instant getCreatedAt() { return createdAt; }
}
