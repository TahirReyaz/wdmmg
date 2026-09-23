package com.wdmmg.expense.group;

import com.wdmmg.expense.common.ApiException;
import com.wdmmg.expense.group.GroupDtos.ShareInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {
    private static final List<Long> MEMBERS = List.of(1L, 2L, 3L);

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void equalSplitDistributesRemainderCents() {
        Map<Long, BigDecimal> r = SplitCalculator.compute(bd("100.00"), SplitType.EQUAL, List.of(), MEMBERS);
        assertThat(r.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        assertThat(r).containsEntry(1L, bd("33.34")).containsEntry(2L, bd("33.33")).containsEntry(3L, bd("33.33"));
    }

    @Test
    void sharesSplitIsProportional() {
        var r = SplitCalculator.compute(bd("10.00"), SplitType.SHARES,
                List.of(new ShareInput(1L, bd("2")), new ShareInput(2L, bd("1"))), MEMBERS);
        assertThat(r).containsEntry(1L, bd("6.67")).containsEntry(2L, bd("3.33"));
    }

    @Test
    void exactSplitMustAddUp() {
        assertThatThrownBy(() -> SplitCalculator.compute(bd("10.00"), SplitType.EXACT,
                List.of(new ShareInput(1L, bd("7"))), MEMBERS)).isInstanceOf(ApiException.class);
    }

    @Test
    void percentSplitMustTotal100() {
        assertThatThrownBy(() -> SplitCalculator.compute(bd("10.00"), SplitType.PERCENT,
                List.of(new ShareInput(1L, bd("50")), new ShareInput(2L, bd("40"))), MEMBERS))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void debtsAreSimplified() {
        Map<Long, BigDecimal> net = new LinkedHashMap<>();
        net.put(1L, bd("66.67"));
        net.put(2L, bd("-33.33"));
        net.put(3L, bd("-33.34"));
        var transfers = DebtSimplifier.simplify(net);
        assertThat(transfers).hasSize(2);
        assertThat(transfers).allMatch(t -> t.to().equals(1L));
    }
}
