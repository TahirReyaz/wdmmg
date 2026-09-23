package com.wdmmg.expense.goal;

import com.wdmmg.expense.goal.GoalDtos.*;
import com.wdmmg.expense.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class GoalController {
    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    public List<GoalResponse> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody GoalRequest req) {
        return service.create(me.id(), req);
    }

    @PutMapping("/{id}")
    public GoalResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody GoalRequest req) {
        return service.update(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me.id(), id);
    }

    /** Set money aside for the goal, or take some back. */
    @PostMapping("/{id}/funds")
    public GoalResponse funds(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody FundsRequest req) {
        return service.moveFunds(me.id(), id, req);
    }

    @GetMapping("/{id}/history")
    public List<ContributionResponse> history(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.history(me.id(), id);
    }
}
