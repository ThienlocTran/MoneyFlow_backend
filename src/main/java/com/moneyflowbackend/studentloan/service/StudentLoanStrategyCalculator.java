package com.moneyflowbackend.studentloan.service;

import com.moneyflowbackend.studentloan.dto.StudentLoanStrategyLoanResponse;
import com.moneyflowbackend.studentloan.dto.StudentLoanStrategyResultResponse;
import com.moneyflowbackend.studentloan.model.StudentLoan;
import com.moneyflowbackend.studentloan.model.StudentLoanPayoffStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StudentLoanStrategyCalculator {
    private static final int MAX_MONTHS = 600;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public List<StudentLoanStrategyResultResponse> compare(List<StudentLoan> loans, BigDecimal extraMonthlyBudget, LocalDate startDate) {
        BigDecimal extra = money(extraMonthlyBudget == null ? BigDecimal.ZERO : extraMonthlyBudget);
        return List.of(
                project(StudentLoanPayoffStrategy.MINIMUM_ONLY, loans, BigDecimal.ZERO, startDate),
                project(StudentLoanPayoffStrategy.AVALANCHE, loans, extra, startDate),
                project(StudentLoanPayoffStrategy.SNOWBALL, loans, extra, startDate));
    }

    private StudentLoanStrategyResultResponse project(
            StudentLoanPayoffStrategy strategy,
            List<StudentLoan> source,
            BigDecimal extra,
            LocalDate startDate) {
        List<PlanLoan> loans = order(strategy, source).stream()
                .filter(loan -> loan.getCurrentPrincipal().compareTo(BigDecimal.ZERO) > 0)
                .map(PlanLoan::new)
                .toList();
        List<StudentLoanStrategyLoanResponse> order = loans.stream()
                .map(loan -> StudentLoanStrategyLoanResponse.builder()
                        .loanId(loan.loan.getId())
                        .name(loan.loan.getName())
                        .build())
                .toList();
        if (loans.isEmpty()) {
            return result(strategy, 0, startDate, BigDecimal.ZERO, BigDecimal.ZERO, false, null, order);
        }

        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        int month = 0;
        while (loans.stream().anyMatch(PlanLoan::hasBalance) && month < MAX_MONTHS) {
            month++;
            BigDecimal totalMonthlyInterest = BigDecimal.ZERO;
            for (PlanLoan loan : loans) {
                if (!loan.hasBalance()) {
                    continue;
                }
                BigDecimal interest = loan.interest();
                loan.balance = money(loan.balance.add(interest));
                totalMonthlyInterest = money(totalMonthlyInterest.add(interest));
            }
            BigDecimal available = loans.stream()
                    .filter(PlanLoan::hasBalance)
                    .map(loan -> loan.loan.getMinimumMonthlyPayment())
                    .reduce(extra, BigDecimal::add);
            if (available.compareTo(totalMonthlyInterest) <= 0) {
                return result(strategy, month, null, totalInterest, totalPaid, true,
                        "Portfolio payment does not cover monthly interest", order);
            }
            for (PlanLoan loan : loans) {
                if (!loan.hasBalance()) {
                    continue;
                }
                BigDecimal payment = loan.loan.getMinimumMonthlyPayment().min(loan.balance);
                loan.balance = money(loan.balance.subtract(payment));
                totalPaid = money(totalPaid.add(payment));
            }
            BigDecimal extraRemaining = extra;
            for (PlanLoan loan : loans) {
                if (!loan.hasBalance() || extraRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal payment = extraRemaining.min(loan.balance);
                loan.balance = money(loan.balance.subtract(payment));
                extraRemaining = money(extraRemaining.subtract(payment));
                totalPaid = money(totalPaid.add(payment));
            }
            totalInterest = money(totalInterest.add(totalMonthlyInterest));
        }
        if (loans.stream().anyMatch(PlanLoan::hasBalance)) {
            return result(strategy, MAX_MONTHS, null, totalInterest, totalPaid, true,
                    "Projection exceeded 600-month safety horizon", order);
        }
        return result(strategy, month, startDate.plusMonths(month), totalInterest, totalPaid, false, null, order);
    }

    private List<StudentLoan> order(StudentLoanPayoffStrategy strategy, List<StudentLoan> loans) {
        Comparator<StudentLoan> comparator = switch (strategy) {
            case MINIMUM_ONLY -> Comparator.comparing(StudentLoan::getId);
            case AVALANCHE -> Comparator.comparing(StudentLoan::getAnnualInterestRate).reversed()
                    .thenComparing(StudentLoan::getCurrentPrincipal)
                    .thenComparing(StudentLoan::getId);
            case SNOWBALL -> Comparator.comparing(StudentLoan::getCurrentPrincipal)
                    .thenComparing(Comparator.comparing(StudentLoan::getAnnualInterestRate).reversed())
                    .thenComparing(StudentLoan::getId);
        };
        return loans.stream().sorted(comparator).toList();
    }

    private StudentLoanStrategyResultResponse result(
            StudentLoanPayoffStrategy strategy,
            int monthCount,
            LocalDate payoffDate,
            BigDecimal interest,
            BigDecimal totalPaid,
            boolean nonAmortizing,
            String reason,
            List<StudentLoanStrategyLoanResponse> order) {
        return StudentLoanStrategyResultResponse.builder()
                .strategy(strategy)
                .monthCount(monthCount)
                .estimatedPayoffDate(payoffDate)
                .projectedInterest(interest)
                .projectedTotalPaid(totalPaid)
                .nonAmortizing(nonAmortizing)
                .nonAmortizingReason(reason)
                .strategyOrder(order)
                .assumptions(assumptions())
                .build();
    }

    private List<String> assumptions() {
        return List.of(
                "Monthly portfolio projection; results are estimates, not promises.",
                "Monthly rate = annual nominal rate / 12.",
                "Minimum payments are applied before user-supplied extra budget.",
                "Extra budget is applied by strategy order until exhausted.",
                "No real payments, transactions, wallet changes, fees, or bank synchronization.",
                "Projection horizon capped at 600 months.");
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, ROUNDING);
    }

    private class PlanLoan {
        private final StudentLoan loan;
        private BigDecimal balance;

        private PlanLoan(StudentLoan loan) {
            this.loan = loan;
            this.balance = money(loan.getCurrentPrincipal());
        }

        private boolean hasBalance() {
            return balance.compareTo(BigDecimal.ZERO) > 0;
        }

        private BigDecimal interest() {
            BigDecimal monthlyRate = loan.getAnnualInterestRate().divide(BigDecimal.valueOf(12), 10, ROUNDING);
            return money(balance.multiply(monthlyRate));
        }
    }
}
