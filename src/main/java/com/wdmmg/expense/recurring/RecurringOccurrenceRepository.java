package com.wdmmg.expense.recurring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RecurringOccurrenceRepository extends JpaRepository<RecurringOccurrence, Long> {
    boolean existsByRecurringIdAndDueDate(Long recurringId, LocalDate dueDate);

    @Query("""
            select o from RecurringOccurrence o join fetch o.recurring r join fetch r.category
            where o.userId = :userId and o.status = com.wdmmg.expense.recurring.OccurrenceStatus.PENDING
            order by o.dueDate, o.id
            """)
    List<RecurringOccurrence> findPending(@Param("userId") Long userId);

    @Query("""
            select o from RecurringOccurrence o join fetch o.recurring r join fetch r.category
            where o.id = :id and o.userId = :userId
            """)
    Optional<RecurringOccurrence> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            select o.recurring.id, count(o) from RecurringOccurrence o
            where o.recurring.id in :ids and o.status = com.wdmmg.expense.recurring.OccurrenceStatus.PENDING
            group by o.recurring.id
            """)
    List<Object[]> countPending(@Param("ids") Collection<Long> ids);

    default Map<Long, Long> pendingCounts(Collection<Long> ids) {
        Map<Long, Long> out = new java.util.HashMap<>();
        if (ids.isEmpty()) return out;
        for (Object[] r : countPending(ids)) out.put((Long) r[0], (Long) r[1]);
        return out;
    }
}
