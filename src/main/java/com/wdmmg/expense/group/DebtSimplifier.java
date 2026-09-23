package com.wdmmg.expense.group;

import java.math.BigDecimal;
import java.util.*;

/** Greedy min-cash-flow: repeatedly settles the largest debtor against the largest creditor. */
public final class DebtSimplifier {
    private DebtSimplifier() {}

    public record Transfer(Long from, Long to, BigDecimal amount) {}

    public static List<Transfer> simplify(Map<Long, BigDecimal> net) {
        PriorityQueue<long[]> creditors = new PriorityQueue<>((a, b) -> Long.compare(b[1], a[1]));
        PriorityQueue<long[]> debtors = new PriorityQueue<>((a, b) -> Long.compare(b[1], a[1]));
        net.forEach((user, amt) -> {
            long c = amt.movePointRight(2).longValue();
            if (c > 0) creditors.add(new long[]{user, c});
            else if (c < 0) debtors.add(new long[]{user, -c});
        });
        List<Transfer> out = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            long[] cr = creditors.poll();
            long[] db = debtors.poll();
            long pay = Math.min(cr[1], db[1]);
            out.add(new Transfer(db[0], cr[0], BigDecimal.valueOf(pay, 2)));
            if (cr[1] - pay > 0) creditors.add(new long[]{cr[0], cr[1] - pay});
            if (db[1] - pay > 0) debtors.add(new long[]{db[0], db[1] - pay});
        }
        return out;
    }
}
