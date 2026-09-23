package com.wdmmg.expense.group;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupExpenseRepository extends JpaRepository<GroupExpense, Long> {

    @EntityGraph(attributePaths = {"paidBy", "createdBy", "category"})
    Page<GroupExpense> findByGroupIdOrderByDateDescIdDesc(Long groupId, Pageable pageable);

    @EntityGraph(attributePaths = {"paidBy", "createdBy", "category", "group"})
    Optional<GroupExpense> findByIdAndGroupId(Long id, Long groupId);

    @Query("""
            select ge from GroupExpense ge join fetch ge.category join fetch ge.paidBy
            where ge.group.id = :groupId and ge.date between :from and :to
            """)
    List<GroupExpense> findInRange(@Param("groupId") Long groupId, @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    // ---- balance aggregates for one group (rows: [userId, sum]) ----
    @Query("select ge.paidBy.id, sum(ge.amount) from GroupExpense ge where ge.group.id = :groupId group by ge.paidBy.id")
    List<Object[]> paidByMember(@Param("groupId") Long groupId);

    @Query("""
            select s.user.id, sum(s.amount) from GroupExpenseShare s
            where s.groupExpense.group.id = :groupId group by s.user.id
            """)
    List<Object[]> owedByMember(@Param("groupId") Long groupId);

    // ---- aggregates for one user across groups (rows: [groupId, sum]) ----
    @Query("select ge.group.id, sum(ge.amount) from GroupExpense ge where ge.group.id in :ids group by ge.group.id")
    List<Object[]> totalByGroup(@Param("ids") Collection<Long> ids);

    @Query("""
            select ge.group.id, sum(ge.amount) from GroupExpense ge
            where ge.paidBy.id = :userId and ge.group.id in :ids group by ge.group.id
            """)
    List<Object[]> paidByUserPerGroup(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Query("""
            select s.groupExpense.group.id, sum(s.amount) from GroupExpenseShare s
            where s.user.id = :userId and s.groupExpense.group.id in :ids group by s.groupExpense.group.id
            """)
    List<Object[]> owedByUserPerGroup(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    /** A user's shares of group expenses in a date range – feeds personal analytics. */
    @Query("""
            select s from GroupExpenseShare s
            join fetch s.groupExpense ge join fetch ge.category join fetch ge.group
            where s.user.id = :userId and s.amount > 0 and ge.date between :from and :to
            order by ge.date desc
            """)
    List<GroupExpenseShare> findUserSharesInRange(@Param("userId") Long userId, @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);
}
