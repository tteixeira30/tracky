package com.tracky.calendar;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Evento financeiro recorrente ou pontual (salário, renda, subscrição, etc.).
 * Valores em EUR (a apresentação converte para a moeda base do utilizador).
 */
@Entity
@Table(name = "calendar_events")
public class CalendarEvent {

    public enum Frequency { MONTHLY, YEARLY, ONCE }
    public enum Category { INCOME, HOUSING, SUBSCRIPTION, BILL, TRANSPORT, FOOD, SAVING, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String name;

    @Enumerated(EnumType.STRING)
    private Category category = Category.OTHER;

    /** true = entrada de dinheiro; false = saída. */
    private boolean inflow = false;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Frequency frequency = Frequency.MONTHLY;

    /** Dia do mês (1-31) para frequência MONTHLY. */
    private Integer dayOfMonth;

    /** Data para ONCE (absoluta) e YEARLY (usa-se o mês/dia). */
    private LocalDate eventDate;

    private boolean active = true;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public boolean isInflow() { return inflow; }
    public void setInflow(boolean inflow) { this.inflow = inflow; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }
    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
