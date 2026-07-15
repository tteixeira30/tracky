package com.tracky.income;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "allocations")
public class Allocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** Mês a que esta categoria pertence, formato AAAA-MM (ex: 2026-07). */
    private String month;

    private String name;

    /** Percentagem do rendimento (null quando a categoria é definida por valor fixo). */
    private BigDecimal percentage;

    /** Valor fixo em euros (null quando a categoria é definida por percentagem). */
    @Column(precision = 19, scale = 2)
    private BigDecimal fixedAmount;

    /** Cor da categoria em hexadecimal (ex: #6366f1). Null → usa a cor por omissão da paleta. */
    private String color;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
    public BigDecimal getFixedAmount() { return fixedAmount; }
    public void setFixedAmount(BigDecimal fixedAmount) { this.fixedAmount = fixedAmount; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
