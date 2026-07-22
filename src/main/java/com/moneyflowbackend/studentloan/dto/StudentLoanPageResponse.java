package com.moneyflowbackend.studentloan.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StudentLoanPageResponse {
    private List<StudentLoanResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
