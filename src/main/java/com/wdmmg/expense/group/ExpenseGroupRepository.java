package com.wdmmg.expense.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {
    @Query("""
            select g from ExpenseGroup g join fetch g.createdBy
            where exists (select 1 from GroupMember m where m.group = g and m.user.id = :userId)
            order by g.createdAt desc
            """)
    List<ExpenseGroup> findForUser(@Param("userId") Long userId);
}
