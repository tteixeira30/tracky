package com.tracky.investment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    List<Investment> findByUserIdOrderByIdAsc(Long userId);
    Optional<Investment> findByIdAndUserId(Long id, Long userId);

    /**
     * Reforço em unidades (ativos com preço live) — soma unidades e custo e avança o
     * marcador atomicamente, só se o marcador ainda estiver no mês esperado. Evita a
     * dupla aplicação entre o scheduler e pedidos concorrentes. Devolve 1 se aplicou.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Investment i SET i.quantity = COALESCE(i.quantity, 0) + :units, "
            + "i.initialValue = COALESCE(i.initialValue, 0) + :amount, i.lastAppliedMonth = :newMonth "
            + "WHERE i.id = :id AND COALESCE(i.lastAppliedMonth, '') = COALESCE(:oldMonth, '')")
    int applyReinforcementUnits(@Param("id") Long id, @Param("units") BigDecimal units,
                                @Param("amount") BigDecimal amount,
                                @Param("oldMonth") String oldMonth, @Param("newMonth") String newMonth);

    /** Reforço em valor (ativos sem preço live) — atómico e condicional ao marcador. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Investment i SET i.fallbackValue = COALESCE(i.fallbackValue, 0) + :amount, "
            + "i.initialValue = COALESCE(i.initialValue, 0) + :amount, i.lastAppliedMonth = :newMonth "
            + "WHERE i.id = :id AND COALESCE(i.lastAppliedMonth, '') = COALESCE(:oldMonth, '')")
    int applyReinforcementValue(@Param("id") Long id, @Param("amount") BigDecimal amount,
                                @Param("oldMonth") String oldMonth, @Param("newMonth") String newMonth);
}
