package com.moneyflowbackend.studentloan.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StudentLoanStrategyLoanResponse {
    private UUID loanId;
    private String name;
}
