package com.tahir.finance.expense.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @Query("SELECT e FROM Expense e WHERE e.id = :id AND e.userId = :userId")
    Optional<Expense> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT e FROM Expense e WHERE e.userId = :userId AND e.idempotencyKey = :key")
    Optional<Expense> findByIdempotencyKey(@Param("userId") UUID userId, @Param("key") String key);

    @Query("""
            SELECT COUNT(e) FROM Expense e
             WHERE e.userId = :userId AND e.categoryId = :categoryId AND e.deletedAt IS NULL
            """)
    long countByCategory(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId);

    @Modifying
    @Query("""
            UPDATE Expense e SET e.categoryId = :target
             WHERE e.userId = :userId AND e.categoryId = :source
            """)
    int reassignCategory(@Param("userId") UUID userId,
                         @Param("source") UUID source,
                         @Param("target") UUID target);
}
