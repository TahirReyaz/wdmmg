package com.wdmmg.expense.tag;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Tag> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    /** [tagId, count, total, firstDate, lastDate] for every tag of the user that has expenses. */
    @Query("""
            select e.tag.id, count(e), sum(e.amount), min(e.date), max(e.date)
            from Expense e where e.user.id = :userId and e.tag is not null
            group by e.tag.id
            """)
    List<Object[]> statsByTag(@Param("userId") Long userId);

    /** [categoryName, categoryColor, count, total] for one tag, largest first. */
    @Query("""
            select c.name, c.color, count(e), sum(e.amount)
            from Expense e join e.category c
            where e.user.id = :userId and e.tag.id = :tagId
            group by c.id, c.name, c.color order by sum(e.amount) desc
            """)
    List<Object[]> byCategory(@Param("userId") Long userId, @Param("tagId") Long tagId);

    /** [paymentMethod, count, total] for one tag, largest first. */
    @Query("""
            select e.paymentMethod, count(e), sum(e.amount)
            from Expense e where e.user.id = :userId and e.tag.id = :tagId
            group by e.paymentMethod order by sum(e.amount) desc
            """)
    List<Object[]> byPaymentMethod(@Param("userId") Long userId, @Param("tagId") Long tagId);
}
