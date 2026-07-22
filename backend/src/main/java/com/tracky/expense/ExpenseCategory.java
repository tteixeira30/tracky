package com.tracky.expense;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Categoria de despesa definida pelo utilizador. As categorias por omissão
 * (GROCERIES, RESTAURANT, …) vivem no código (ver ExpenseController.DEFAULT_KEYS e
 * categories.js no frontend); esta entidade guarda apenas as categorias
 * <em>personalizadas</em> que cada utilizador cria.
 *
 * <p>O {@link #catKey} é o identificador estável guardado em cada movimento
 * ({@link Transaction#getCategory()}); o {@link #label} e a {@link #color} são só de
 * apresentação e podem ser editados sem afetar os movimentos.
 */
@Entity
@Table(name = "expense_categories")
public class ExpenseCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** Chave estável usada nos movimentos (ex.: "EDUCACAO"). Única por utilizador. */
    @Column(name = "cat_key", length = 60)
    private String catKey;

    @Column(length = 60)
    private String label;

    /** Cor de apresentação em hex (#rrggbb). */
    @Column(length = 20)
    private String color;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCatKey() { return catKey; }
    public void setCatKey(String catKey) { this.catKey = catKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Instant getCreatedAt() { return createdAt; }
}
