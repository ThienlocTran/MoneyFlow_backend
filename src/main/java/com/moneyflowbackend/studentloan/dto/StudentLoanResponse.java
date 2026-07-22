package com.moneyflowbackend.studentloan.dto;

import com.moneyflowbackend.studentloan.model.StudentLoanStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class StudentLoanResponse {
    private UUID id;
    private UUID workspaceId;
    private String name;
    private String lender;
    private BigDecimal originalPrincipal;
    private BigDecimal currentPrincipal;
    private BigDecimal annualInterestRate;
    private BigDecimal minimumMonthlyPayment;
    private BigDecimal plannedExtraMonthlyPayment;
    private LocalDate startDate;
    private LocalDate targetPayoffDate;
    private StudentLoanStatus status;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
