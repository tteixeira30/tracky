package com.tracky.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {
    List<CategoryRule> findByUserIdOrderByIdAsc(Long userId);
    Optional<CategoryRule> findByUserIdAndMatchKey(Long userId, String matchKey);
    Optional<CategoryRule> findByIdAndUserId(Long id, Long userId);
}
