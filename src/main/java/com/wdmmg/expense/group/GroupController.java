package com.wdmmg.expense.group;

import com.wdmmg.expense.analytics.AnalyticsDtos.GroupAnalytics;
import com.wdmmg.expense.analytics.AnalyticsService;
import com.wdmmg.expense.common.PageResponse;
import com.wdmmg.expense.group.GroupDtos.*;
import com.wdmmg.expense.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService service;
    private final AnalyticsService analytics;

    public GroupController(GroupService service, AnalyticsService analytics) {
        this.service = service;
        this.analytics = analytics;
    }

    /** All my groups with my balance in each, plus totals across groups. */
    @GetMapping
    public OverallBalance list(@AuthenticationPrincipal AuthUser me) {
        return service.overview(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetail create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody GroupRequest req) {
        return service.create(me, req);
    }

    @GetMapping("/{groupId}")
    public GroupDetail get(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId) {
        return service.detail(me, groupId);
    }

    @PutMapping("/{groupId}")
    public GroupDetail update(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                              @Valid @RequestBody GroupRequest req) {
        return service.update(me, groupId, req);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId) {
        service.delete(me, groupId);
    }

    // ---- members ----
    @PostMapping("/{groupId}/members")
    public GroupDetail addMember(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                                 @Valid @RequestBody AddMemberRequest req) {
        return service.addMember(me, groupId, req);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                             @PathVariable Long userId) {
        service.removeMember(me, groupId, userId);
    }

    // ---- expenses ----
    @GetMapping("/{groupId}/expenses")
    public PageResponse<GroupExpenseResponse> expenses(@AuthenticationPrincipal AuthUser me,
                                                       @PathVariable Long groupId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return service.listExpenses(me, groupId, page, size);
    }

    @PostMapping("/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupExpenseResponse addExpense(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                                           @Valid @RequestBody GroupExpenseRequest req) {
        return service.addExpense(me, groupId, req);
    }

    @PutMapping("/{groupId}/expenses/{expenseId}")
    public GroupExpenseResponse updateExpense(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                                              @PathVariable Long expenseId,
                                              @Valid @RequestBody GroupExpenseRequest req) {
        return service.updateExpense(me, groupId, expenseId, req);
    }

    @DeleteMapping("/{groupId}/expenses/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                              @PathVariable Long expenseId) {
        service.deleteExpense(me, groupId, expenseId);
    }

    // ---- settlements ----
    @GetMapping("/{groupId}/settlements")
    public List<SettlementResponse> settlements(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId) {
        return service.listSettlements(me, groupId);
    }

    @PostMapping("/{groupId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse settle(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                                     @Valid @RequestBody SettlementRequest req) {
        return service.settle(me, groupId, req);
    }

    @DeleteMapping("/{groupId}/settlements/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSettlement(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                                 @PathVariable Long settlementId) {
        service.deleteSettlement(me, groupId, settlementId);
    }

    // ---- analytics ----
    @GetMapping("/{groupId}/analytics")
    public GroupAnalytics analytics(@AuthenticationPrincipal AuthUser me, @PathVariable Long groupId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.group(me, groupId, from, to);
    }
}
