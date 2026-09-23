package com.wdmmg.expense.expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    List<Expense> findAll(Specification<Expense> spec, Sort sort);

    @Query("select e from Expense e join fetch e.category where e.id = :id and e.user.id = :userId")
    Optional<Expense> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            select e from Expense e join fetch e.category
            where e.user.id = :userId and e.date between :from and :to
            order by e.date desc, e.id desc
            """)
    List<Expense> findInRange(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.user.id = :userId and e.date between :from and :to")
    java.math.BigDecimal sum(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
