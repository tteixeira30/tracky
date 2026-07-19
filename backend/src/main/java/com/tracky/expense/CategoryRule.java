package com.tracky.expense;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Regra de categorização aprendida do utilizador: movimentos cuja descrição
 * normalizada seja igual a {@link #matchKey} recebem {@link #category}.
 * Criada quando o utilizador recategoriza um movimento com "aplicar a todos";
 * usada nas importações seguintes (tem prioridade sobre a categorização
 * automática por palavras-chave do frontend).
 */
@Entity
@Table(name = "category_rules")
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    /** Descrição normalizada (trim, minúsculas, espaços colapsados). */
    @Column(name = "match_key", length = 500)
    private String matchKey;

    @Enumerated(EnumType.STRING)
    private Transaction.Category category = Transaction.Category.OTHER;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMatchKey() { return matchKey; }
    public void setMatchKey(String matchKey) { this.matchKey = matchKey; }
    public Transaction.Category getCategory() { return category == null ? Transaction.Category.OTHER : category; }
    public void setCategory(Transaction.Category category) {
        this.category = category == null ? Transaction.Category.OTHER : category;
    }
    public Instant getCreatedAt() { return createdAt; }
}
