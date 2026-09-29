package com.wdmmg.expense.group;

import com.wdmmg.expense.category.Category;
import com.wdmmg.expense.category.CategoryDtos.CategoryResponse;
import com.wdmmg.expense.category.CategoryService;
import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.Money;
import com.wdmmg.expense.common.PageResponse;
import com.wdmmg.expense.common.AppClock;
import com.wdmmg.expense.notification.NotificationService;
import com.wdmmg.expense.notification.NotificationType;
import com.wdmmg.expense.group.GroupDtos.*;
import com.wdmmg.expense.security.AuthUser;
import com.wdmmg.expense.user.User;
import com.wdmmg.expense.user.UserDtos.UserSummary;
import com.wdmmg.expense.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupService {
    private final ExpenseGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupExpenseRepository expenses;
    private final SettlementRepository settlements;
    private final UserRepository users;
    private final CategoryService categories;
    private final NotificationService notifications;
    private final AppClock clock;

    public GroupService(ExpenseGroupRepository groups, GroupMemberRepository members, GroupExpenseRepository expenses,
                        SettlementRepository settlements, UserRepository users, CategoryService categories,
                        NotificationService notifications, AppClock clock) {
        this.groups = groups;
        this.members = members;
        this.expenses = expenses;
        this.settlements = settlements;
        this.users = users;
        this.categories = categories;
        this.notifications = notifications;
        this.clock = clock;
    }

    // =====================================================================
    // Groups
    // =====================================================================

    @Transactional(readOnly = true)
    public OverallBalance overview(Long userId) {
        List<ExpenseGroup> mine = groups.findForUser(userId);
        if (mine.isEmpty()) return new OverallBalance(Money.zero(), Money.zero(), Money.zero(), List.of());
        List<Long> ids = mine.stream().map(ExpenseGroup::getId).toList();

        Map<Long, BigDecimal> total = toMap(expenses.totalByGroup(ids));
        Map<Long, BigDecimal> paid = toMap(expenses.paidByUserPerGroup(userId, ids));
        Map<Long, BigDecimal> owed = toMap(expenses.owedByUserPerGroup(userId, ids));
        Map<Long, BigDecimal> sent = toMap(settlements.sentByUserPerGroup(userId, ids));
        Map<Long, BigDecimal> recv = toMap(settlements.receivedByUserPerGroup(userId, ids));
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : members.countByGroupIds(ids)) counts.put((Long) r[0], (Long) r[1]);

        BigDecimal youOwe = BigDecimal.ZERO, youAreOwed = BigDecimal.ZERO;
        List<GroupSummary> list = new ArrayList<>();
        for (ExpenseGroup g : mine) {
            Long id = g.getId();
            BigDecimal net = get(paid, id).subtract(get(owed, id)).add(get(sent, id)).subtract(get(recv, id));
            if (net.signum() < 0) youOwe = youOwe.add(net.negate());
            else youAreOwed = youAreOwed.add(net);
            list.add(new GroupSummary(id, g.getName(), g.getDescription(), UserSummary.from(g.getCreatedBy()),
                    g.getCreatedAt(), counts.getOrDefault(id, 0L), Money.of(get(total, id)), Money.of(net)));
        }
        return new OverallBalance(Money.of(youOwe), Money.of(youAreOwed), Money.of(youAreOwed.subtract(youOwe)), list);
    }

    @Transactional
    public GroupDetail create(AuthUser me, GroupRequest req) {
        User creator = users.getReferenceById(me.id());
        ExpenseGroup g = new ExpenseGroup();
        g.setName(req.name().trim());
        g.setDescription(blankToNull(req.description()));
        g.setCreatedBy(creator);
        groups.save(g);
        members.save(new GroupMember(g, creator));
        return detail(me, g.getId());
    }

    @Transactional
    public GroupDetail update(AuthUser me, Long groupId, GroupRequest req) {
        ExpenseGroup g = requireMember(me.id(), groupId);
        g.setName(req.name().trim());
        g.setDescription(blankToNull(req.description()));
        return detail(me, groupId);
    }

    @Transactional
    public void delete(AuthUser me, Long groupId) {
        ExpenseGroup g = requireMember(me.id(), groupId);
        if (!g.getCreatedBy().getId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("Only the group creator can delete this group");
        }
        groups.delete(g);
    }

    @Transactional(readOnly = true)
    public GroupDetail detail(AuthUser me, Long groupId) {
        ExpenseGroup g = requireMember(me.id(), groupId);
        List<GroupMember> ms = members.findMembers(groupId);
        Map<Long, BigDecimal> paid = toMap(expenses.paidByMember(groupId));
        Map<Long, BigDecimal> owed = toMap(expenses.owedByMember(groupId));
        Map<Long, BigDecimal> sent = toMap(settlements.sentByMember(groupId));
        Map<Long, BigDecimal> recv = toMap(settlements.receivedByMember(groupId));

        Map<Long, UserSummary> people = new LinkedHashMap<>();
        ms.forEach(m -> people.put(m.getUser().getId(), UserSummary.from(m.getUser())));
        Set<Long> currentIds = new HashSet<>(people.keySet());
        // Former members that still have history must stay in the balance sheet.
        Set<Long> involved = new LinkedHashSet<>(people.keySet());
        involved.addAll(paid.keySet());
        involved.addAll(owed.keySet());
        involved.addAll(sent.keySet());
        involved.addAll(recv.keySet());
        for (Long id : involved) {
            people.computeIfAbsent(id, k -> users.findById(k).map(UserSummary::from)
                    .orElse(new UserSummary(k, "Unknown", "", null, null)));
        }

        List<MemberBalance> balances = new ArrayList<>();
        Map<Long, BigDecimal> net = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var e : people.entrySet()) {
            Long id = e.getKey();
            BigDecimal n = get(paid, id).subtract(get(owed, id)).add(get(sent, id)).subtract(get(recv, id));
            net.put(id, n);
            total = total.add(get(paid, id));
            balances.add(new MemberBalance(e.getValue(), Money.of(get(paid, id)), Money.of(get(owed, id)),
                    Money.of(get(sent, id)), Money.of(get(recv, id)), Money.of(n), currentIds.contains(id)));
        }
        List<Debt> debts = DebtSimplifier.simplify(net).stream()
                .map(t -> new Debt(people.get(t.from()), people.get(t.to()), t.amount())).toList();

        return new GroupDetail(g.getId(), g.getName(), g.getDescription(), UserSummary.from(g.getCreatedBy()),
                g.getCreatedAt(), Money.of(total), Money.of(net.getOrDefault(me.id(), BigDecimal.ZERO)),
                balances, debts);
    }

    // =====================================================================
    // Members
    // =====================================================================

    @Transactional
    public GroupDetail addMember(AuthUser me, Long groupId, AddMemberRequest req) {
        ExpenseGroup g = requireMember(me.id(), groupId);
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "No registered user with that email – ask them to sign up first"));
        if (members.existsByGroupIdAndUserId(groupId, u.getId())) {
            throw ApiException.conflict(u.getName() + " is already in this group");
        }
        members.save(new GroupMember(g, u));
        notifications.notify(u.getId(), NotificationType.GROUP_ADDED,
                me.name() + " added you to " + g.getName(), "/groups/" + groupId, null, groupId);
        return detail(me, groupId);
    }

    /** Remove a member (creator can remove anyone, anyone can remove themselves = leave). */
    @Transactional
    public void removeMember(AuthUser me, Long groupId, Long userId) {
        ExpenseGroup g = requireMember(me.id(), groupId);
        boolean self = me.id().equals(userId);
        boolean owner = g.getCreatedBy().getId().equals(me.id());
        if (!self && !owner) throw ApiException.forbidden("Only the group creator can remove other members");
        if (g.getCreatedBy().getId().equals(userId)) {
            throw ApiException.badRequest("The group creator cannot leave; delete the group instead");
        }
        GroupMember m = members.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> ApiException.notFound("Member"));
        BigDecimal net = detail(me, groupId).members().stream()
                .filter(b -> b.user().id().equals(userId)).map(MemberBalance::net).findFirst().orElse(BigDecimal.ZERO);
        if (net.signum() != 0) {
            throw ApiException.badRequest("Balance must be settled (currently " + net.toPlainString() + ") before leaving");
        }
        members.delete(m);
    }

    // =====================================================================
    // Group expenses
    // =====================================================================

    @Transactional(readOnly = true)
    public PageResponse<GroupExpenseResponse> listExpenses(AuthUser me, Long groupId, int page, int size) {
        requireMember(me.id(), groupId);
        var p = expenses.findByGroupIdOrderByDateDescIdDesc(groupId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.of(p, ge -> toResponse(ge, me.id()));
    }

    @Transactional
    public GroupExpenseResponse addExpense(AuthUser me, Long groupId, GroupExpenseRequest req) {
        ExpenseGroup g = requireMember(me.id(), groupId);
        GroupExpense ge = new GroupExpense();
        ge.setGroup(g);
        ge.setCreatedBy(users.getReferenceById(me.id()));
        ge.setCategory(categories.requireUsable(req.categoryId()));
        applyExpense(ge, groupId, req);
        expenses.save(ge);
        notifyShares(me, g, ge);
        return toResponse(ge, me.id());
    }

    @Transactional
    public GroupExpenseResponse updateExpense(AuthUser me, Long groupId, Long expenseId, GroupExpenseRequest req) {
        requireMember(me.id(), groupId);
        GroupExpense ge = expenses.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> ApiException.notFound("Group expense"));
        requireCanModify(me, ge);
        if (!ge.getCategory().getId().equals(req.categoryId())) {
            ge.setCategory(categories.requireUsable(req.categoryId()));
        }
        ge.getShares().clear();
        expenses.flush(); // remove old shares before inserting new ones (unique constraint)
        applyExpense(ge, groupId, req);
        expenses.flush();
        return toResponse(ge, me.id());
    }

    @Transactional
    public void deleteExpense(AuthUser me, Long groupId, Long expenseId) {
        requireMember(me.id(), groupId);
        GroupExpense ge = expenses.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> ApiException.notFound("Group expense"));
        requireCanModify(me, ge);
        expenses.delete(ge);
    }

    private void applyExpense(GroupExpense ge, Long groupId, GroupExpenseRequest req) {
        Set<Long> memberIds = members.findMembers(groupId).stream()
                .map(m -> m.getUser().getId()).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!memberIds.contains(req.paidById())) throw ApiException.badRequest("The payer must be a group member");

        BigDecimal amount = Money.of(req.amount());
        Map<Long, BigDecimal> split = SplitCalculator.compute(amount, req.splitType(), req.shares(), memberIds);
        for (Long uid : split.keySet()) {
            if (!memberIds.contains(uid)) throw ApiException.badRequest("Everyone in the split must be a group member");
        }

        ge.setName(req.name().trim());
        ge.setAmount(amount);
        ge.setDate(req.date());
        ge.setPaidBy(users.getReferenceById(req.paidById()));
        ge.setSplitType(req.splitType());
        ge.setNotes(blankToNull(req.notes()));
        split.forEach((uid, amt) -> ge.getShares().add(new GroupExpenseShare(ge, users.getReferenceById(uid), amt)));
    }

    /** Tells everyone with a share (except whoever added it) about a new shared expense. */
    private void notifyShares(AuthUser me, ExpenseGroup g, GroupExpense ge) {
        for (GroupExpenseShare s : ge.getShares()) {
            Long uid = s.getUser().getId();
            if (uid.equals(me.id()) || s.getAmount().signum() == 0) continue;
            notifications.notify(uid, NotificationType.GROUP_EXPENSE,
                    me.name() + " added “" + ge.getName() + "” in " + g.getName(), "/groups/" + g.getId(), s.getAmount(), ge.getId());
        }
    }

    private void requireCanModify(AuthUser me, GroupExpense ge) {
        Long uid = me.id();
        boolean ok = ge.getCreatedBy().getId().equals(uid) || ge.getPaidBy().getId().equals(uid)
                || ge.getGroup().getCreatedBy().getId().equals(uid) || me.isAdmin();
        if (!ok) throw ApiException.forbidden("Only the payer, the person who added it, or the group creator can change this");
    }

    private GroupExpenseResponse toResponse(GroupExpense ge, Long meId) {
        BigDecimal mine = BigDecimal.ZERO;
        List<ShareResponse> shares = new ArrayList<>();
        for (GroupExpenseShare s : ge.getShares()) {
            User u = s.getUser();
            shares.add(new ShareResponse(UserSummary.from(u), s.getAmount()));
            if (u.getId().equals(meId)) mine = s.getAmount();
        }
        Category c = ge.getCategory();
        return new GroupExpenseResponse(ge.getId(), ge.getGroup().getId(), ge.getName(), ge.getAmount(), ge.getDate(),
                CategoryResponse.from(c), UserSummary.from(ge.getPaidBy()), UserSummary.from(ge.getCreatedBy()),
                ge.getSplitType(), ge.getNotes(), shares, Money.of(mine), ge.getCreatedAt());
    }

    // =====================================================================
    // Settlements
    // =====================================================================

    @Transactional(readOnly = true)
    public List<SettlementResponse> listSettlements(AuthUser me, Long groupId) {
        requireMember(me.id(), groupId);
        return settlements.findForGroup(groupId).stream().map(GroupService::toSettlementResponse).toList();
    }

    @Transactional
    public SettlementResponse settle(AuthUser me, Long groupId, SettlementRequest req) {
        ExpenseGroup g = requireMember(me.id(), groupId);
        // The person recording a payment is always the one who paid.
        if (req.toUserId().equals(me.id())) throw ApiException.badRequest("Choose who you paid");
        if (!members.existsByGroupIdAndUserId(groupId, req.toUserId())) {
            throw ApiException.badRequest("You can only pay someone in this group");
        }
        Settlement s = new Settlement();
        s.setGroup(g);
        s.setFromUser(users.getReferenceById(me.id()));
        s.setToUser(users.getReferenceById(req.toUserId()));
        s.setAmount(Money.of(req.amount()));
        s.setDate(req.date() == null ? clock.today() : req.date());
        s.setNote(blankToNull(req.note()));
        settlements.save(s);
        notifications.notify(req.toUserId(), NotificationType.PAYMENT_RECEIVED,
                me.name() + " paid you back in " + g.getName(), "/groups/" + groupId, s.getAmount(), s.getId());
        return toSettlementResponse(settlements.findById(s.getId()).orElseThrow());
    }

    @Transactional
    public void deleteSettlement(AuthUser me, Long groupId, Long settlementId) {
        ExpenseGroup g = requireMember(me.id(), groupId);
        Settlement s = settlements.findByIdAndGroupId(settlementId, groupId)
                .orElseThrow(() -> ApiException.notFound("Settlement"));
        Long uid = me.id();
        if (!s.getFromUser().getId().equals(uid) && !s.getToUser().getId().equals(uid)
                && !g.getCreatedBy().getId().equals(uid)) {
            throw ApiException.forbidden("Only the people involved or the group creator can delete this");
        }
        settlements.delete(s);
    }

    private static SettlementResponse toSettlementResponse(Settlement s) {
        return new SettlementResponse(s.getId(), UserSummary.from(s.getFromUser()), UserSummary.from(s.getToUser()),
                s.getAmount(), s.getDate(), s.getNote(), s.getCreatedAt());
    }

    // =====================================================================
    // helpers
    // =====================================================================

    ExpenseGroup requireMember(Long userId, Long groupId) {
        ExpenseGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Group"));
        if (!members.existsByGroupIdAndUserId(groupId, userId)) throw ApiException.notFound("Group");
        return g;
    }

    private static Map<Long, BigDecimal> toMap(List<Object[]> rows) {
        Map<Long, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) m.put((Long) r[0], (BigDecimal) r[1]);
        return m;
    }

    private static BigDecimal get(Map<Long, BigDecimal> m, Long k) {
        return m.getOrDefault(k, BigDecimal.ZERO);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
