package com.moneyflowbackend.studentloan.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class StudentLoanProjectionResponse {
    private UUID loanId;
    private LocalDate estimatedPayoffDate;
    private int monthCount;
    private BigDecimal totalProjectedInterest;
    private BigDecimal totalProjectedPayments;
    private BigDecimal scheduledMonthlyPayment;
    private boolean nonAmortizing;
    private String nonAmortizingReason;
    private List<String> assumptions;
    private List<StudentLoanProjectionMonthResponse> schedule;
    private int schedulePage;
    private int scheduleSize;
    private int scheduleTotalElements;
}
