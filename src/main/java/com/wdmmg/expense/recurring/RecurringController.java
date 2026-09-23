package com.wdmmg.expense.recurring;

import com.wdmmg.expense.recurring.RecurringDtos.*;
import com.wdmmg.expense.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recurring")
public class RecurringController {
    private final RecurringService service;

    public RecurringController(RecurringService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecurringResponse> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody RecurringRequest req) {
        return service.create(me.id(), req);
    }

    @PutMapping("/{id}")
    public RecurringResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody RecurringRequest req) {
        return service.update(me.id(), id, req);
    }

    @PostMapping("/{id}/pause")
    public RecurringResponse pause(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.setActive(me.id(), id, false);
    }

    @PostMapping("/{id}/resume")
    public RecurringResponse resume(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.setActive(me.id(), id, true);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me.id(), id);
    }

    /** Due items waiting for confirmation. */
    @GetMapping("/pending")
    public List<OccurrenceResponse> pending(@AuthenticationPrincipal AuthUser me) {
        return service.pending(me.id());
    }

    @PostMapping("/occurrences/{id}/confirm")
    public OccurrenceResponse confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @Valid @RequestBody(required = false) ConfirmRequest req) {
        return service.confirm(me.id(), id, req);
    }

    @PostMapping("/occurrences/{id}/skip")
    public OccurrenceResponse skip(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.skip(me.id(), id);
    }
}
