package com.wdmmg.expense.expense;

import com.wdmmg.expense.tag.TagService;
import com.wdmmg.expense.category.CategoryService;
import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.Money;
import com.wdmmg.expense.common.PageResponse;
import com.wdmmg.expense.expense.ExpenseDtos.*;
import com.wdmmg.expense.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
public class ExpenseService {
    private static final Set<String> SORTABLE = Set.of("date", "amount", "name", "createdAt");

    private final ExpenseRepository repo;
    private final UserRepository users;
    private final CategoryService categories;
    private final TagService tags;

    @PersistenceContext
    private EntityManager em;

    public ExpenseService(ExpenseRepository repo, UserRepository users, CategoryService categories, TagService tags) {
        this.repo = repo;
        this.users = users;
        this.categories = categories;
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> search(Long userId, ExpenseFilter filter, int page, int size,
                                                String sortBy, String dir) {
        String field = SORTABLE.contains(sortBy) ? sortBy : "date";
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(repo.findAll(ExpenseSpecs.forUser(userId, filter), pageable), ExpenseResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> all(Long userId, ExpenseFilter filter) {
        return repo.findAll(ExpenseSpecs.forUser(userId, filter), Sort.by(Sort.Direction.DESC, "date", "id"))
                .stream().map(ExpenseResponse::from).toList();
    }

    /** One aggregate query with the same filters as the list, so the total covers every page. */
    @Transactional(readOnly = true)
    public ExpenseTotal total(Long userId, ExpenseFilter filter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Expense> root = q.from(Expense.class);
        q.multiselect(cb.count(root), cb.sum(root.<BigDecimal>get("amount")))
                .where(ExpenseSpecs.forUser(userId, filter).toPredicate(root, q, cb));
        Tuple t = em.createQuery(q).getSingleResult();
        Long count = t.get(0, Long.class);
        BigDecimal sum = t.get(1, BigDecimal.class);
        return new ExpenseTotal(count == null ? 0 : count, Money.of(sum));
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long userId, Long id) {
        return ExpenseResponse.from(owned(userId, id));
    }

    @Transactional
    public ExpenseResponse create(Long userId, ExpenseRequest req) {
        Expense e = new Expense();
        e.setUser(users.getReferenceById(userId));
        apply(e, req);
        e.setTag(tags.resolve(userId, req.tagId()));
        repo.save(e);
        return ExpenseResponse.from(e);
    }

    @Transactional
    public ExpenseResponse update(Long userId, Long id, ExpenseRequest req) {
        Expense e = owned(userId, id);
        // Allow keeping a retired category on an old expense, but not switching to one.
        if (!e.getCategory().getId().equals(req.categoryId())) {
            e.setCategory(categories.requireUsable(req.categoryId()));
        }
        e.setName(req.name().trim());
        e.setAmount(Money.of(req.amount()));
        e.setDate(req.date());
        e.setPaymentMethod(req.paymentMethod() == null ? PaymentMethod.OTHER : req.paymentMethod());
        e.setNotes(blankToNull(req.notes()));
        e.setTag(tags.resolve(userId, req.tagId()));
        repo.flush();
        return ExpenseResponse.from(e);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repo.delete(owned(userId, id));
    }

    private Expense owned(Long userId, Long id) {
        return repo.findOwned(id, userId).orElseThrow(() -> ApiException.notFound("Expense"));
    }

    private void apply(Expense e, ExpenseRequest req) {
        e.setCategory(categories.requireUsable(req.categoryId()));
        e.setName(req.name().trim());
        e.setAmount(Money.of(req.amount()));
        e.setDate(req.date());
        e.setPaymentMethod(req.paymentMethod() == null ? PaymentMethod.OTHER : req.paymentMethod());
        e.setNotes(blankToNull(req.notes()));
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
