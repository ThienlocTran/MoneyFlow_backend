package com.moneyflowbackend.sinkingfund.dto;

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
public class SinkingFundSummaryPageResponse {
    private List<SinkingFundSummaryResponse> content;
    private BigDecimal activeWorkspaceReservedTotal;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
