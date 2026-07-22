package com.moneyflowbackend.studentloan.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class StudentLoanProjectionMonthResponse {
    private int monthNumber;
    private LocalDate paymentDate;
    private BigDecimal startingPrincipal;
    private BigDecimal interest;
    private BigDecimal principal;
    private BigDecimal payment;
    private BigDecimal endingPrincipal;
}
