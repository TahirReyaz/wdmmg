package com.wdmmg.expense.recurring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {
    @Query("select r from RecurringExpense r join fetch r.category where r.userId = :userId order by r.active desc, r.nextDueDate asc nulls last, r.id")
    List<RecurringExpense> findForUser(@Param("userId") Long userId);

    @Query("select r from RecurringExpense r join fetch r.category where r.id = :id and r.userId = :userId")
    Optional<RecurringExpense> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select r.id from RecurringExpense r where r.active = true and r.nextDueDate is not null and r.nextDueDate <= :today")
    List<Long> findDueIds(@Param("today") LocalDate today);
}
