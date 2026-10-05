package com.wdmmg.expense.tag;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.common.Money;
import com.wdmmg.expense.tag.TagDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    public static final int MAX_TAGS_PER_USER = 200;

    private final TagRepository tags;

    public TagService(TagRepository tags) {
        this.tags = tags;
    }

    /** Newest first, each with its count, total and date range (one grouped query for all of them). */
    @Transactional(readOnly = true)
    public List<TagResponse> list(Long userId) {
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] row : tags.statsByTag(userId)) stats.put((Long) row[0], row);
        return tags.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(t -> toResponse(t, stats.get(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TagSummary summary(Long userId, Long id) {
        Tag t = owned(userId, id);
        Object[] stats = tags.statsByTag(userId).stream().filter(r -> id.equals(r[0])).findFirst().orElse(null);
        TagResponse tag = toResponse(t, stats);
        long days = tag.firstDate() == null ? 0 : ChronoUnit.DAYS.between(tag.firstDate(), tag.lastDate()) + 1;
        BigDecimal perDay = days == 0 ? Money.zero() : tag.total().divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        List<Slice> byCategory = tags.byCategory(userId, id).stream()
                .map(r -> new Slice((String) r[0], (String) r[1], (Long) r[2], Money.of((BigDecimal) r[3])))
                .toList();
        List<Slice> byMethod = tags.byPaymentMethod(userId, id).stream()
                .map(r -> new Slice(String.valueOf(r[0]), null, (Long) r[1], Money.of((BigDecimal) r[2])))
                .toList();
        return new TagSummary(tag, days, perDay, byCategory, byMethod);
    }

    @Transactional
    public TagResponse create(Long userId, TagRequest req) {
        String name = cleanName(req.name());
        if (tags.existsByUserIdAndNameIgnoreCase(userId, name)) throw duplicate(name);
        if (tags.findByUserIdOrderByCreatedAtDescIdDesc(userId).size() >= MAX_TAGS_PER_USER) {
            throw ApiException.badRequest("You can have up to " + MAX_TAGS_PER_USER + " tags. Delete one you no longer need.");
        }
        Tag t = new Tag();
        t.setUserId(userId);
        t.setName(name);
        if (req.color() != null) t.setColor(req.color().toLowerCase());
        tags.save(t);
        return toResponse(t, null);
    }

    @Transactional
    public TagResponse update(Long userId, Long id, TagRequest req) {
        Tag t = owned(userId, id);
        String name = cleanName(req.name());
        if (tags.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) throw duplicate(name);
        t.setName(name);
        if (req.color() != null) t.setColor(req.color().toLowerCase());
        tags.flush();
        Object[] stats = tags.statsByTag(userId).stream().filter(r -> id.equals(r[0])).findFirst().orElse(null);
        return toResponse(t, stats);
    }

    /** Expenses keep existing and become untagged (ON DELETE SET NULL). */
    @Transactional
    public void delete(Long userId, Long id) {
        tags.delete(owned(userId, id));
    }

    /** For expense create/update: null = no tag; someone else's or unknown id = 400. */
    @Transactional(readOnly = true)
    public Tag resolve(Long userId, Long tagId) {
        if (tagId == null) return null;
        return tags.findByIdAndUserId(tagId, userId).orElseThrow(() -> ApiException.badRequest("That tag doesn't exist"));
    }

    private Tag owned(Long userId, Long id) {
        return tags.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Tag"));
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw ApiException.badRequest("Give the tag a name");
        return name;
    }

    private static ApiException duplicate(String name) {
        return ApiException.conflict("You already have a tag called \"" + name + "\"");
    }

    private static TagResponse toResponse(Tag t, Object[] stats) {
        long count = stats == null ? 0 : (Long) stats[1];
        BigDecimal total = stats == null ? Money.zero() : Money.of((BigDecimal) stats[2]);
        LocalDate first = stats == null ? null : (LocalDate) stats[3];
        LocalDate last = stats == null ? null : (LocalDate) stats[4];
        return new TagResponse(t.getId(), t.getName(), t.getColor(), count, total, first, last, t.getCreatedAt());
    }
}
