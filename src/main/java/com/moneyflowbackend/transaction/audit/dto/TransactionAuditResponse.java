package com.moneyflowbackend.transaction.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionAuditResponse {
    private UUID id;
    private UUID transactionId;
    private String action;
    private Actor actor;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private Instant createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Actor {
        private UUID id;
        private String username;
        private String displayName;
    }
}
