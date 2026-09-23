package com.wdmmg.expense.goal;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.AppClock;
import com.wdmmg.expense.common.Money;
import com.wdmmg.expense.goal.GoalDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class GoalService {
    private final GoalRepository goals;
    private final GoalContributionRepository contributions;
    private final AppClock clock;

    public GoalService(GoalRepository goals, GoalContributionRepository contributions, AppClock clock) {
        this.goals = goals;
        this.contributions = contributions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId) {
        return goals.findByUserIdOrderByCreatedAtAsc(userId).stream().map(g -> GoalResponse.from(g, clock.today())).toList();
    }

    @Transactional
    public GoalResponse create(Long userId, GoalRequest req) {
        Goal g = new Goal();
        g.setUserId(userId);
        apply(g, req);
        goals.save(g);
        return GoalResponse.from(g, clock.today());
    }

    @Transactional
    public GoalResponse update(Long userId, Long id, GoalRequest req) {
        Goal g = owned(userId, id);
        apply(g, req);
        return GoalResponse.from(g, clock.today());
    }

    /** Deleting releases whatever was set aside back to "available". */
    @Transactional
    public void delete(Long userId, Long id) {
        goals.delete(owned(userId, id));
    }

    @Transactional
    public GoalResponse moveFunds(Long userId, Long id, FundsRequest req) {
        Goal g = owned(userId, id);
        BigDecimal amount = Money.of(req.amount());
        BigDecimal delta = req.direction() == Direction.ADD ? amount : amount.negate();
        BigDecimal next = g.getSavedAmount().add(delta);
        if (next.signum() < 0) {
            throw ApiException.badRequest("Only " + g.getSavedAmount().toPlainString() + " is set aside in this goal");
        }
        g.setSavedAmount(next);
        GoalContribution c = new GoalContribution();
        c.setGoalId(g.getId());
        c.setAmount(delta);
        c.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        contributions.save(c);
        return GoalResponse.from(g, clock.today());
    }

    @Transactional(readOnly = true)
    public List<ContributionResponse> history(Long userId, Long id) {
        owned(userId, id);
        return contributions.findTop50ByGoalIdOrderByCreatedAtDescIdDesc(id).stream().map(ContributionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalSaved(Long userId) {
        return Money.of(goals.sumSaved(userId));
    }

    private void apply(Goal g, GoalRequest req) {
        g.setName(req.name().trim());
        g.setTargetAmount(Money.of(req.targetAmount()));
        g.setTargetDate(req.targetDate());
    }

    private Goal owned(Long userId, Long id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Goal"));
    }
}
