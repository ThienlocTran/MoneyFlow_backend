package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReserveAllocationListResponse(List<ReserveAllocationResponse> reserves, int count, int limit,
                                            BigDecimal totalActiveAmount, BigDecimal totalReleasedAmount,
                                            BigDecimal totalCancelledAmount, String currency, List<String> warnings) {
}
