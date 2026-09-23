package com.tahir.finance.expense.domain;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filters built with the Criteria API rather than a JPQL string full of
 * ":param IS NULL OR ..." clauses - those defeat the query planner and make
 * enum parameters awkward to bind.
 */
public final class ExpenseSpecifications {

    private ExpenseSpecifications() {
    }

    public static Specification<Expense> filter(UUID userId,
                                                Instant from,
                                                Instant to,
                                                UUID categoryId,
                                                UUID tripId,
                                                ExpenseOrigin origin,
                                                Long minAmount,
                                                Long maxAmount,
                                                String search,
                                                Instant cursorSpentAt,
                                                UUID cursorId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("spentAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.<Instant>get("spentAt"), to));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (tripId != null) {
                predicates.add(cb.equal(root.get("tripId"), tripId));
            }
            if (origin != null) {
                predicates.add(cb.equal(root.get("origin"), origin));
            }
            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Long>get("amountMinor"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Long>get("amountMinor"), maxAmount));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.<String>get("merchant"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("note"), "")), pattern)));
            }

            // Keyset pagination: everything strictly "older" than the cursor,
            // with the id breaking ties between expenses at the same instant.
            if (cursorSpentAt != null && cursorId != null) {
                predicates.add(cb.or(
                        cb.lessThan(root.<Instant>get("spentAt"), cursorSpentAt),
                        cb.and(
                                cb.equal(root.get("spentAt"), cursorSpentAt),
                                cb.lessThan(root.<UUID>get("id"), cursorId))));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
