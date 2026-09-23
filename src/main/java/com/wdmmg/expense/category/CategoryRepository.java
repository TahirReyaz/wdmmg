package com.wdmmg.expense.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    Optional<Category> findByNameIgnoreCase(String name);

    @Query("select count(e) from Expense e where e.category.id = :id")
    long countPersonalUsage(@Param("id") Long id);

    @Query("select count(g) from GroupExpense g where g.category.id = :id")
    long countGroupUsage(@Param("id") Long id);
}
