package com.moneyflowbackend.closing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DailyClosingResponse {
    private UUID closingId;
    private LocalDate closingDate;
    private String status;
    private String note;
    private Instant completedAt;
    private UUID completedByUserId;
    private List<DailyClosingWalletResponse> wallets;
}
