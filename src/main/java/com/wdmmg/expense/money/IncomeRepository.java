package com.wdmmg.expense.money;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IncomeRepository extends JpaRepository<Income, Long> {
    @Query("""
            select i from Income i where i.userId = :userId and i.date between :from and :to
            order by i.date desc, i.id desc
            """)
    Page<Income> findPage(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("select i from Income i where i.userId = :userId and i.date between :from and :to")
    List<Income> findInRange(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(i.amount), 0) from Income i where i.userId = :userId and i.date between :from and :to")
    BigDecimal sum(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    Optional<Income> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndTypeAndDateBetween(Long userId, IncomeType type, LocalDate from, LocalDate to);

    Optional<Income> findFirstByUserIdAndTypeOrderByDateDescIdDesc(Long userId, IncomeType type);
}
