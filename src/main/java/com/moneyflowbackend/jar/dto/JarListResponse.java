package com.moneyflowbackend.jar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JarListResponse {
    private List<JarResponse> jars;
    private BigDecimal activeAllocationTotal;
    private boolean allocationValid;
    private String allocationWarning;
}
