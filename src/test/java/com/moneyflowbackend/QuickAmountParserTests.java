package com.moneyflowbackend;

import com.moneyflowbackend.quickentry.parser.QuickAmountParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuickAmountParserTests {
    private final QuickAmountParser parser = new QuickAmountParser();

    @Test
    void parsesSupportedVndAmounts() {
        assertAmount("35k", "35000");
        assertAmount("35 K", "35000");
        assertAmount("35 ngh\u00ECn", "35000");
        assertAmount("35 ng\u00E0n", "35000");
        assertAmount("35000", "35000");
        assertAmount("35.000", "35000");
        assertAmount("35,000", "35000");
        assertAmount("1tr", "1000000");
        assertAmount("1.5tr", "1500000");
        assertAmount("1,5 tri\u1EC7u", "1500000");
        assertAmount("2tr500", "2500000");
        assertAmount("2 tri\u1EC7u 500", "2500000");
        assertAmount("2 tri\u1EC7u 500 ngh\u00ECn", "2500000");
        assertAmount("500\u0111", "500");
        assertAmount("500 vnd", "500");
    }

    @Test
    void appliesWorkspaceThousandFallbackOnlyForSingleBareAmount() {
        var result = parser.parse("an sang 35", "THOUSAND");
        assertThat(result.single()).isPresent();
        assertThat(result.single().orElseThrow().amount()).isEqualByComparingTo("35000");
        assertThat(result.single().orElseThrow().assumedThousand()).isTrue();

        assertThat(parser.parse("15/06", "THOUSAND").single()).isEmpty();
        assertThat(parser.parse("08:30", "THOUSAND").single()).isEmpty();
        assertThat(parser.parse("2026", "THOUSAND").single()).isEmpty();
        assertThat(parser.parse("35000", "THOUSAND").single().orElseThrow().amount()).isEqualByComparingTo("35000");
    }

    @Test
    void rejectsZeroNegativeAndAmbiguousAmounts() {
        assertThat(parser.parse("0", "UNIT").zeroAmount()).isTrue();
        assertThat(parser.parse("-35k", "UNIT").negativeAmount()).isTrue();
        assertThat(parser.parse("35k 40k", "UNIT").ambiguous()).isTrue();
    }

    private void assertAmount(String input, String expected) {
        var result = parser.parse(input, "UNIT");
        assertThat(result.single()).isPresent();
        assertThat(result.single().orElseThrow().amount()).isEqualByComparingTo(new BigDecimal(expected));
    }
}