package com.moneyflowbackend;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.service.RecurrenceCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceCalculatorTests {
    private final RecurrenceCalculator calculator = new RecurrenceCalculator();

    @Test
    void weeklyKeepsAnchorWeekdayAndInterval() {
        assertThat(calculate(LocalDate.of(2026, 7, 6), null, ObligationFrequency.WEEKLY, 1,
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 20)))
                .containsExactly(
                        LocalDate.of(2026, 7, 6),
                        LocalDate.of(2026, 7, 13),
                        LocalDate.of(2026, 7, 20));

        assertThat(calculate(LocalDate.of(2026, 7, 6), null, ObligationFrequency.WEEKLY, 2,
                LocalDate.of(2026, 7, 7), LocalDate.of(2026, 8, 3)))
                .containsExactly(
                        LocalDate.of(2026, 7, 20),
                        LocalDate.of(2026, 8, 3));
    }

    @Test
    void weeklyDoesNotGenerateBeforeStartDate() {
        assertThat(calculate(LocalDate.of(2026, 7, 20), null, ObligationFrequency.WEEKLY, 1,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 19)))
                .isEmpty();
    }

    @Test
    void monthlyClampsWithoutAnchorDrift() {
        assertThat(calculate(LocalDate.of(2026, 1, 31), null, ObligationFrequency.MONTHLY, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30)))
                .containsExactly(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 4, 30));
    }

    @Test
    void monthlyHandlesCommonAnchorDaysAndLeapFebruary() {
        assertThat(calculate(LocalDate.of(2026, 1, 1), null, ObligationFrequency.MONTHLY, 1,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .containsExactly(LocalDate.of(2026, 2, 1));
        assertThat(calculate(LocalDate.of(2026, 1, 28), null, ObligationFrequency.MONTHLY, 1,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .containsExactly(LocalDate.of(2026, 2, 28));
        assertThat(calculate(LocalDate.of(2024, 1, 29), null, ObligationFrequency.MONTHLY, 1,
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)))
                .containsExactly(LocalDate.of(2024, 2, 29));
        assertThat(calculate(LocalDate.of(2026, 1, 30), null, ObligationFrequency.MONTHLY, 1,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .containsExactly(LocalDate.of(2026, 2, 28));
    }

    @Test
    void monthlySupportsIntervals() {
        assertThat(calculate(LocalDate.of(2026, 1, 31), null, ObligationFrequency.MONTHLY, 2,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 31)))
                .containsExactly(
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 3, 31),
                        LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 7, 31));
        assertThat(calculate(LocalDate.of(2026, 1, 31), null, ObligationFrequency.MONTHLY, 3,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 10, 31)))
                .containsExactly(
                        LocalDate.of(2026, 4, 30),
                        LocalDate.of(2026, 7, 31),
                        LocalDate.of(2026, 10, 31));
    }

    @Test
    void yearlyHandlesLeapDayWithoutDrift() {
        assertThat(calculate(LocalDate.of(2024, 2, 29), null, ObligationFrequency.YEARLY, 1,
                LocalDate.of(2024, 1, 1), LocalDate.of(2028, 12, 31)))
                .containsExactly(
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2025, 2, 28),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2027, 2, 28),
                        LocalDate.of(2028, 2, 29));
    }

    @Test
    void yearlySupportsNormalDatesAndIntervalTwo() {
        assertThat(calculate(LocalDate.of(2026, 8, 5), null, ObligationFrequency.YEARLY, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31)))
                .containsExactly(
                        LocalDate.of(2026, 8, 5),
                        LocalDate.of(2027, 8, 5),
                        LocalDate.of(2028, 8, 5));
        assertThat(calculate(LocalDate.of(2024, 2, 29), null, ObligationFrequency.YEARLY, 2,
                LocalDate.of(2024, 1, 1), LocalDate.of(2030, 12, 31)))
                .containsExactly(
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2026, 2, 28),
                        LocalDate.of(2028, 2, 29),
                        LocalDate.of(2030, 2, 28));
    }

    @Test
    void endDateAndWindowAreInclusive() {
        assertThat(calculate(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 10, 5), ObligationFrequency.MONTHLY, 1,
                LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5)))
                .containsExactly(
                        LocalDate.of(2026, 9, 5),
                        LocalDate.of(2026, 10, 5));
        assertThat(calculate(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 31), ObligationFrequency.MONTHLY, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isEmpty();
    }

    @Test
    void rejectsInvalidWindow() {
        assertThatThrownBy(() -> calculate(LocalDate.of(2026, 8, 5), null, ObligationFrequency.MONTHLY, 1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 31)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_DATE_RANGE");
    }

    private List<LocalDate> calculate(
            LocalDate startDate,
            LocalDate endDate,
            ObligationFrequency frequency,
            int intervalCount,
            LocalDate fromDate,
            LocalDate toDate) {
        return calculator.calculate(startDate, endDate, frequency, intervalCount, fromDate, toDate);
    }
}
