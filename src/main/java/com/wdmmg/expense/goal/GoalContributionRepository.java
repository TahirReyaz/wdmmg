package com.wdmmg.expense.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {
    List<GoalContribution> findTop50ByGoalIdOrderByCreatedAtDescIdDesc(Long goalId);
}
