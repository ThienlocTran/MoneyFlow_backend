package com.moneyflowbackend.quickentry.dto;

import com.moneyflowbackend.transaction.dto.TransactionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickEntryBatchConfirmResponse {
    private String idempotencyKey;
    private UUID voiceRecordId;
    private int committedCount;
    private boolean idempotentReplay;
    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String candidateId;
        private TransactionResponse transaction;
    }
}
