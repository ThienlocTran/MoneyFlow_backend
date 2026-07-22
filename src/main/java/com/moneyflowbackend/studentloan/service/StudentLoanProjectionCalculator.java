package com.moneyflowbackend.studentloan.service;

import com.moneyflowbackend.studentloan.dto.StudentLoanProjectionMonthResponse;
import com.moneyflowbackend.studentloan.dto.StudentLoanProjectionResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StudentLoanProjectionCalculator {
    static final int MONEY_SCALE = 2;
    static final int RATE_SCALE = 10;
    static final int MAX_MONTHS = 600;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public StudentLoanProjectionResponse project(
            UUID loanId,
            BigDecimal principal,
            BigDecimal annualInterestRate,
            BigDecimal minimumMonthlyPayment,
            BigDecimal plannedExtraMonthlyPayment,
            LocalDate startDate,
            boolean includeSchedule,
            int schedulePage,
            int scheduleSize) {
        BigDecimal currentPrincipal = money(principal);
        BigDecimal scheduledPayment = money(minimumMonthlyPayment.add(
                plannedExtraMonthlyPayment == null ? BigDecimal.ZERO : plannedExtraMonthlyPayment));
        BigDecimal monthlyRate = annualInterestRate.divide(BigDecimal.valueOf(12), RATE_SCALE, ROUNDING);
        LocalDate effectiveStart = startDate == null ? LocalDate.now() : startDate;
        List<String> assumptions = assumptions();
        if (currentPrincipal.compareTo(BigDecimal.ZERO) == 0) {
            return response(loanId, effectiveStart, 0, BigDecimal.ZERO, BigDecimal.ZERO, scheduledPayment,
                    false, null, assumptions, List.of(), schedulePage, scheduleSize, includeSchedule);
        }
        BigDecimal firstInterest = interest(currentPrincipal, monthlyRate);
        if (scheduledPayment.compareTo(firstInterest) <= 0) {
            return response(loanId, null, 0, BigDecimal.ZERO, BigDecimal.ZERO, scheduledPayment,
                    true, "Scheduled payment does not cover monthly interest", assumptions, List.of(), schedulePage, scheduleSize, includeSchedule);
        }

        BigDecimal remaining = currentPrincipal;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPayments = BigDecimal.ZERO;
        List<StudentLoanProjectionMonthResponse> months = new ArrayList<>();
        int month = 0;
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && month < MAX_MONTHS) {
            month++;
            BigDecimal start = remaining;
            BigDecimal monthlyInterest = interest(start, monthlyRate);
            BigDecimal balanceWithInterest = start.add(monthlyInterest);
            BigDecimal payment = scheduledPayment.min(balanceWithInterest);
            BigDecimal principalPaid = payment.subtract(monthlyInterest).max(BigDecimal.ZERO);
            remaining = money(balanceWithInterest.subtract(payment).max(BigDecimal.ZERO));
            totalInterest = money(totalInterest.add(monthlyInterest));
            totalPayments = money(totalPayments.add(payment));
            months.add(StudentLoanProjectionMonthResponse.builder()
                    .monthNumber(month)
                    .paymentDate(effectiveStart.plusMonths(month))
                    .startingPrincipal(start)
                    .interest(monthlyInterest)
                    .principal(principalPaid)
                    .payment(payment)
                    .endingPrincipal(remaining)
                    .build());
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            return response(loanId, null, MAX_MONTHS, totalInterest, totalPayments, scheduledPayment,
                    true, "Projection exceeded 600-month safety horizon", assumptions, months, schedulePage, scheduleSize, includeSchedule);
        }
        return response(loanId, effectiveStart.plusMonths(month), month, totalInterest, totalPayments, scheduledPayment,
                false, null, assumptions, months, schedulePage, scheduleSize, includeSchedule);
    }

    private StudentLoanProjectionResponse response(
            UUID loanId,
            LocalDate payoffDate,
            int monthCount,
            BigDecimal totalInterest,
            BigDecimal totalPayments,
            BigDecimal scheduledPayment,
            boolean nonAmortizing,
            String reason,
            List<String> assumptions,
            List<StudentLoanProjectionMonthResponse> months,
            int schedulePage,
            int scheduleSize,
            boolean includeSchedule) {
        int safePage = Math.max(schedulePage, 0);
        int safeSize = Math.min(Math.max(scheduleSize, 1), 120);
        int from = Math.min(safePage * safeSize, months.size());
        int to = Math.min(from + safeSize, months.size());
        return StudentLoanProjectionResponse.builder()
                .loanId(loanId)
                .estimatedPayoffDate(payoffDate)
                .monthCount(monthCount)
                .totalProjectedInterest(totalInterest)
                .totalProjectedPayments(totalPayments)
                .scheduledMonthlyPayment(scheduledPayment)
                .nonAmortizing(nonAmortizing)
                .nonAmortizingReason(reason)
                .assumptions(assumptions)
                .schedule(includeSchedule ? months.subList(from, to) : List.of())
                .schedulePage(safePage)
                .scheduleSize(safeSize)
                .scheduleTotalElements(months.size())
                .build();
    }

    private List<String> assumptions() {
        return List.of(
                "Monthly projection; results are estimates, not promises.",
                "Monthly rate = annual nominal rate / 12.",
                "Payment applies to accrued interest first, then principal.",
                "No fees, penalties, capitalization, deferment, subsidies, or variable-rate changes.",
                "Money rounded to 2 decimals with HALF_UP; rates rounded to 10 decimals.",
                "Projection horizon capped at 600 months.");
    }

    private BigDecimal interest(BigDecimal principal, BigDecimal monthlyRate) {
        return money(principal.multiply(monthlyRate));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }
}
