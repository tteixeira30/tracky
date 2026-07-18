package com.tracky.expense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserIdAndTxDateBetweenOrderByTxDateDescIdDesc(Long userId, LocalDate from, LocalDate to);
    List<Transaction> findByUserIdAndAccountIdAndTxDateBetween(Long userId, Long accountId, LocalDate from, LocalDate to);
    List<Transaction> findByUserId(Long userId);
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndAccountId(Long userId, Long accountId);
    void deleteByUserIdAndAccountId(Long userId, Long accountId);
}
