package com.wdmmg.expense.expense;

import com.wdmmg.expense.common.PageResponse;
import com.wdmmg.expense.expense.ExpenseDtos.*;
import com.wdmmg.expense.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ExpenseResponse> list(
            @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        var filter = new ExpenseFilter(from, to, categoryId, paymentMethod, q, minAmount, maxAmount, tagId);
        return service.search(me.id(), filter, page, size, sort, dir);
    }

    /** Count and total of all expenses matching the same filters as the list (every page). */
    @GetMapping("/total")
    public ExpenseTotal total(
            @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) Long tagId) {
        return service.total(me.id(), new ExpenseFilter(from, to, categoryId, paymentMethod, q, minAmount, maxAmount, tagId));
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.get(me.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ExpenseRequest req) {
        return service.create(me.id(), req);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                  @Valid @RequestBody ExpenseRequest req) {
        return service.update(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me.id(), id);
    }

    /** CSV export honouring the same filters as the list endpoint. */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long tagId) {
        var rows = service.all(me.id(), new ExpenseFilter(from, to, categoryId, paymentMethod, q, null, null, tagId));
        StringBuilder sb = new StringBuilder("Date,Name,Category,Tag,Amount,Payment Method,Notes\n");
        for (var r : rows) {
            sb.append(r.date()).append(',')
                    .append(csv(r.name())).append(',')
                    .append(csv(r.category().name())).append(',')
                    .append(csv(r.tag() == null ? null : r.tag().name())).append(',')
                    .append(r.amount().toPlainString()).append(',')
                    .append(r.paymentMethod()).append(',')
                    .append(csv(r.notes())).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expenses.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        // Neutralise spreadsheet formula injection.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s + "\"";
    }
}
