package com.tracky.income;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Item de detalhe dentro de uma categoria (Allocation). Permite escrutinar o que
 * é gasto em cada categoria — ex: categoria "Subscrições" com itens Netflix, Claude, HBO.
 * É apenas detalhe informativo: não altera o valor orçamentado da categoria.
 */
@Entity
@Table(name = "allocation_items")
public class AllocationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** Categoria a que este item pertence. */
    @Column(name = "allocation_id")
    private Long allocationId;

    private String name;

    /** Valor gasto neste item, em euros. */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAllocationId() { return allocationId; }
    public void setAllocationId(Long allocationId) { this.allocationId = allocationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
