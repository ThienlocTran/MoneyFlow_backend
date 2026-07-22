package com.moneyflowbackend;

import com.moneyflowbackend.studentloan.dto.StudentLoanProjectionResponse;
import com.moneyflowbackend.studentloan.service.StudentLoanProjectionCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentLoanProjectionCalculatorTests {
    private final StudentLoanProjectionCalculator calculator = new StudentLoanProjectionCalculator();

    @Test
    void projectsZeroInterestWithFinalPartialPayment() {
        StudentLoanProjectionResponse response = calculator.project(
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                new BigDecimal("300.00"),
                BigDecimal.ZERO,
                LocalDate.of(2026, 7, 1),
                true,
                0,
                10);

        assertThat(response.isNonAmortizing()).isFalse();
        assertThat(response.getMonthCount()).isEqualTo(4);
        assertThat(response.getEstimatedPayoffDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(response.getTotalProjectedInterest()).isEqualByComparingTo("0.00");
        assertThat(response.getTotalProjectedPayments()).isEqualByComparingTo("1000.00");
        assertThat(response.getSchedule()).hasSize(4);
        assertThat(response.getSchedule().getLast().getPayment()).isEqualByComparingTo("100.00");
    }

    @Test
    void detectsNonAmortizingWhenPaymentDoesNotCoverInterest() {
        StudentLoanProjectionResponse response = calculator.project(
                UUID.randomUUID(),
                new BigDecimal("12000.00"),
                new BigDecimal("0.120000"),
                new BigDecimal("120.00"),
                BigDecimal.ZERO,
                LocalDate.of(2026, 7, 1),
                false,
                0,
                10);

        assertThat(response.isNonAmortizing()).isTrue();
        assertThat(response.getNonAmortizingReason()).contains("interest");
        assertThat(response.getSchedule()).isEmpty();
        assertThat(response.getEstimatedPayoffDate()).isNull();
    }

    @Test
    void appliesInterestFirstAndPaginatesSchedule() {
        StudentLoanProjectionResponse response = calculator.project(
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                new BigDecimal("0.120000"),
                new BigDecimal("200.00"),
                new BigDecimal("50.00"),
                LocalDate.of(2026, 7, 1),
                true,
                1,
                2);

        assertThat(response.isNonAmortizing()).isFalse();
        assertThat(response.getScheduledMonthlyPayment()).isEqualByComparingTo("250.00");
        assertThat(response.getTotalProjectedInterest()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getSchedule()).hasSize(2);
        assertThat(response.getScheduleTotalElements()).isGreaterThan(2);
        assertThat(response.getAssumptions()).anySatisfy(value -> assertThat(value).contains("estimates"));
    }
}
