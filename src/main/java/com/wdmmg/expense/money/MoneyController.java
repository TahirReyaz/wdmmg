package com.wdmmg.expense.money;

import com.wdmmg.expense.common.AppClock;
import com.wdmmg.expense.common.PageResponse;
import com.wdmmg.expense.money.MoneyDtos.*;
import com.wdmmg.expense.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/money")
public class MoneyController {
    private final MoneyService service;
    private final AppClock clock;

    public MoneyController(MoneyService service, AppClock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/summary")
    public MoneySummary summary(@AuthenticationPrincipal AuthUser me) {
        return service.summary(me.id());
    }

    /** Movements in and out of the balance; defaults to the current month. */
    @GetMapping("/activity")
    public List<ActivityItem> activity(@AuthenticationPrincipal AuthUser me,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : clock.today();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        return service.activity(me.id(), start, end);
    }

    @PutMapping("/opening-balance")
    public MoneySummary opening(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody OpeningBalanceRequest req) {
        return service.setOpening(me.id(), req);
    }

    // ---- income ----
    @GetMapping("/income")
    public PageResponse<IncomeResponse> income(@AuthenticationPrincipal AuthUser me,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.listIncome(me.id(), from, to, page, size);
    }

    @PostMapping("/income")
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse addIncome(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody IncomeRequest req) {
        return service.createIncome(me.id(), req);
    }

    @PutMapping("/income/{id}")
    public IncomeResponse updateIncome(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody IncomeRequest req) {
        return service.updateIncome(me.id(), id, req);
    }

    @DeleteMapping("/income/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIncome(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.deleteIncome(me.id(), id);
    }

    // ---- salary ----
    @GetMapping("/salary-prompt")
    public SalaryPrompt salaryPrompt(@AuthenticationPrincipal AuthUser me) {
        return service.salaryPrompt(me.id());
    }

    @PostMapping("/salary")
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse salary(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SalaryRequest req) {
        return service.recordSalary(me.id(), req);
    }

    @PostMapping("/salary-prompt/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal AuthUser me) {
        service.dismissSalaryPrompt(me.id());
    }

    @PutMapping("/salary-settings")
    public SalaryPrompt salarySettings(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SalarySettingsRequest req) {
        return service.updateSalarySettings(me.id(), req);
    }
}
