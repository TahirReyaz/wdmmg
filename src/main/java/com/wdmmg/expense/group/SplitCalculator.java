package com.wdmmg.expense.group;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.group.GroupDtos.ShareInput;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Turns a split request into exact per-user amounts (in cents, always summing to the total). */
public final class SplitCalculator {
    private SplitCalculator() {}

    public static Map<Long, BigDecimal> compute(BigDecimal total, SplitType type, List<ShareInput> inputs,
                                                Collection<Long> allMembers) {
        List<ShareInput> in = inputs == null ? List.of() : inputs;
        Set<Long> seen = new HashSet<>();
        for (ShareInput s : in) {
            if (!seen.add(s.userId())) throw ApiException.badRequest("A member appears twice in the split");
        }
        long totalCents = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

        return switch (type) {
            case EQUAL -> {
                List<Long> people = in.isEmpty() ? new ArrayList<>(allMembers)
                        : in.stream().map(ShareInput::userId).toList();
                if (people.isEmpty()) throw ApiException.badRequest("Pick at least one person to split with");
                Map<Long, BigDecimal> w = new LinkedHashMap<>();
                people.forEach(p -> w.put(p, BigDecimal.ONE));
                yield allocate(totalCents, w);
            }
            case EXACT -> {
                Map<Long, BigDecimal> out = new LinkedHashMap<>();
                BigDecimal sum = BigDecimal.ZERO;
                for (ShareInput s : in) {
                    BigDecimal v = requirePositiveOrZero(s.value()).setScale(2, RoundingMode.HALF_UP);
                    if (v.signum() > 0) out.put(s.userId(), v);
                    sum = sum.add(v);
                }
                if (sum.compareTo(total.setScale(2, RoundingMode.HALF_UP)) != 0) {
                    throw ApiException.badRequest("Exact amounts add up to " + sum.toPlainString()
                            + " but the expense is " + total.toPlainString());
                }
                if (out.isEmpty()) throw ApiException.badRequest("Nobody has a share in this expense");
                yield out;
            }
            case PERCENT -> {
                Map<Long, BigDecimal> w = weights(in);
                BigDecimal sum = w.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                if (sum.subtract(BigDecimal.valueOf(100)).abs().compareTo(new BigDecimal("0.01")) > 0) {
                    throw ApiException.badRequest("Percentages add up to " + sum.stripTrailingZeros().toPlainString()
                            + "%, they must add up to 100%");
                }
                yield allocate(totalCents, w);
            }
            case SHARES -> allocate(totalCents, weights(in));
        };
    }

    private static Map<Long, BigDecimal> weights(List<ShareInput> in) {
        Map<Long, BigDecimal> w = new LinkedHashMap<>();
        for (ShareInput s : in) {
            BigDecimal v = requirePositiveOrZero(s.value());
            if (v.signum() > 0) w.put(s.userId(), v);
        }
        if (w.isEmpty()) throw ApiException.badRequest("Nobody has a share in this expense");
        return w;
    }

    private static BigDecimal requirePositiveOrZero(BigDecimal v) {
        if (v == null) throw ApiException.badRequest("Each participant needs a value for this split type");
        if (v.signum() < 0) throw ApiException.badRequest("Split values cannot be negative");
        return v;
    }

    /** Largest-remainder allocation of totalCents proportionally to weights. */
    static Map<Long, BigDecimal> allocate(long totalCents, Map<Long, BigDecimal> weights) {
        BigDecimal sumW = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, Long> cents = new LinkedHashMap<>();
        List<Map.Entry<Long, BigDecimal>> fractions = new ArrayList<>();
        long assigned = 0;
        for (var e : weights.entrySet()) {
            BigDecimal exact = BigDecimal.valueOf(totalCents).multiply(e.getValue()).divide(sumW, 10, RoundingMode.DOWN);
            long floor = exact.setScale(0, RoundingMode.DOWN).longValue();
            cents.put(e.getKey(), floor);
            assigned += floor;
            fractions.add(Map.entry(e.getKey(), exact.subtract(BigDecimal.valueOf(floor))));
        }
        fractions.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        long remainder = totalCents - assigned;
        for (int i = 0; remainder > 0; i = (i + 1) % fractions.size(), remainder--) {
            Long id = fractions.get(i).getKey();
            cents.put(id, cents.get(id) + 1);
        }
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        cents.forEach((k, v) -> out.put(k, BigDecimal.valueOf(v, 2)));
        return out;
    }
}
