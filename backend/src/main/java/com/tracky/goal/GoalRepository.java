package com.tracky.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByUserIdOrderByIdAsc(Long userId);
    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    /**
     * Deposita {@code amount} e avança o marcador de forma atómica, mas só se o marcador
     * ainda estiver no mês esperado — protege contra dupla aplicação quando o scheduler e
     * um pedido concorrente atuam sobre o mesmo objetivo. Devolve 1 se aplicou, 0 se outro
     * já tinha avançado o marcador entretanto.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Goal g SET g.savedAmount = COALESCE(g.savedAmount, 0) + :amount, "
            + "g.lastAppliedMonth = :newMonth "
            + "WHERE g.id = :id AND COALESCE(g.lastAppliedMonth, '') = COALESCE(:oldMonth, '')")
    int applyAutoDeposit(@Param("id") Long id, @Param("amount") BigDecimal amount,
                         @Param("oldMonth") String oldMonth, @Param("newMonth") String newMonth);

    /** Incremento atómico do valor poupado (com piso a zero), sem read-modify-write. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Goal g SET g.savedAmount = CASE WHEN COALESCE(g.savedAmount, 0) + :amount < 0 "
            + "THEN 0 ELSE COALESCE(g.savedAmount, 0) + :amount END "
            + "WHERE g.id = :id AND g.userId = :userId")
    int addToSavedAmount(@Param("id") Long id, @Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
