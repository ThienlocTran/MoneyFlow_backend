package com.moneyflowbackend;

import com.moneyflowbackend.studentloan.dto.StudentLoanStrategyResultResponse;
import com.moneyflowbackend.studentloan.model.StudentLoan;
import com.moneyflowbackend.studentloan.model.StudentLoanPayoffStrategy;
import com.moneyflowbackend.studentloan.service.StudentLoanStrategyCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentLoanStrategyCalculatorTests {
    private final StudentLoanStrategyCalculator calculator = new StudentLoanStrategyCalculator();

    @Test
    void avalancheAndSnowballUseDeterministicStrategyOrder() {
        StudentLoan highRate = loan("High", "5000", "0.100000", "150");
        StudentLoan lowBalance = loan("Small", "1000", "0.030000", "50");

        List<StudentLoanStrategyResultResponse> results = calculator.compare(
                List.of(lowBalance, highRate), new BigDecimal("200"), LocalDate.of(2026, 7, 1));

        StudentLoanStrategyResultResponse avalanche = find(results, StudentLoanPayoffStrategy.AVALANCHE);
        StudentLoanStrategyResultResponse snowball = find(results, StudentLoanPayoffStrategy.SNOWBALL);
        assertThat(avalanche.getStrategyOrder().getFirst().getLoanId()).isEqualTo(highRate.getId());
        assertThat(snowball.getStrategyOrder().getFirst().getLoanId()).isEqualTo(lowBalance.getId());
        assertThat(avalanche.isNonAmortizing()).isFalse();
        assertThat(snowball.isNonAmortizing()).isFalse();
    }

    @Test
    void detectsInsufficientPortfolioPayment() {
        StudentLoan loan = loan("Bad", "12000", "0.120000", "50");

        StudentLoanStrategyResultResponse result = find(
                calculator.compare(List.of(loan), BigDecimal.ZERO, LocalDate.of(2026, 7, 1)),
                StudentLoanPayoffStrategy.MINIMUM_ONLY);

        assertThat(result.isNonAmortizing()).isTrue();
        assertThat(result.getNonAmortizingReason()).contains("interest");
    }

    private StudentLoanStrategyResultResponse find(List<StudentLoanStrategyResultResponse> results, StudentLoanPayoffStrategy strategy) {
        return results.stream().filter(result -> result.getStrategy() == strategy).findFirst().orElseThrow();
    }

    private StudentLoan loan(String name, String principal, String rate, String minimum) {
        return StudentLoan.builder()
                .id(UUID.randomUUID())
                .name(name)
                .currentPrincipal(new BigDecimal(principal))
                .annualInterestRate(new BigDecimal(rate))
                .minimumMonthlyPayment(new BigDecimal(minimum))
                .build();
    }
}
