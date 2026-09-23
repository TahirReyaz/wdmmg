package com.tahir.finance.category.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {

    @Query("""
            SELECT c FROM ExpenseCategory c
             WHERE (c.userId = :userId OR c.userId IS NULL)
               AND (:includeArchived = true OR c.archived = false)
             ORDER BY c.sortOrder ASC, lower(c.name) ASC
            """)
    List<ExpenseCategory> findVisible(@Param("userId") UUID userId,
                                      @Param("includeArchived") boolean includeArchived);

    @Query("SELECT c FROM ExpenseCategory c WHERE c.id = :id AND (c.userId = :userId OR c.userId IS NULL)")
    Optional<ExpenseCategory> findVisibleById(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(c) > 0 FROM ExpenseCategory c
             WHERE c.userId = :userId AND lower(c.name) = lower(:name) AND c.id <> :excludeId
            """)
    boolean nameTaken(@Param("userId") UUID userId,
                      @Param("name") String name,
                      @Param("excludeId") UUID excludeId);
}
