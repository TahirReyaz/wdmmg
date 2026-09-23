package com.wdmmg.expense.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    @Query("select coalesce(sum(g.savedAmount), 0) from Goal g where g.userId = :userId")
    BigDecimal sumSaved(@Param("userId") Long userId);
}
