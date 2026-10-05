package com.wdmmg.expense.tag;

import com.wdmmg.expense.security.AuthUser;
import com.wdmmg.expense.tag.TagDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tags")
public class TagController {
    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping
    public List<TagResponse> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me.id());
    }

    @GetMapping("/{id}/summary")
    public TagSummary summary(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return service.summary(me.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TagRequest req) {
        return service.create(me.id(), req);
    }

    @PutMapping("/{id}")
    public TagResponse update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody TagRequest req) {
        return service.update(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.delete(me.id(), id);
    }
}
