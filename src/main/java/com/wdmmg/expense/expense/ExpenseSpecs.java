package com.wdmmg.expense.expense;

import com.wdmmg.expense.expense.ExpenseDtos.ExpenseFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

final class ExpenseSpecs {
    private ExpenseSpecs() {}

    static Specification<Expense> forUser(Long userId, ExpenseFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("user").get("id"), userId));
            if (f.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("date"), f.from()));
            if (f.to() != null) p.add(cb.lessThanOrEqualTo(root.get("date"), f.to()));
            if (f.categoryId() != null) p.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            if (f.tagId() != null) p.add(cb.equal(root.get("tag").get("id"), f.tagId()));
            if (f.paymentMethod() != null) p.add(cb.equal(root.get("paymentMethod"), f.paymentMethod()));
            if (f.minAmount() != null) p.add(cb.greaterThanOrEqualTo(root.get("amount"), f.minAmount()));
            if (f.maxAmount() != null) p.add(cb.lessThanOrEqualTo(root.get("amount"), f.maxAmount()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("notes")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
