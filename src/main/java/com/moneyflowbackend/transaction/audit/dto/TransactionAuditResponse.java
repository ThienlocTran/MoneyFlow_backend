package com.moneyflowbackend.transaction.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private UUID workspaceId;
    private String action;
    private String actionLabel;
    private UUID actorId;
    private String actorName;
    private Actor actor;
    private String sourceType;
    private String sourceLabel;
    private String summary;
    private java.util.List<Change> changes;
    private Context context;
    private Map<String, Object> technicalPayload;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private Instant occurredAt;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Change {
        private String field;
        private String fieldLabel;
        private Object beforeRaw;
        private Object afterRaw;
        private String beforeDisplay;
        private String afterDisplay;
        private String valueType;
        @JsonProperty("isTechnical")
        private boolean technical;
        @JsonProperty("isImportant")
        private boolean important;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Context {
        private UUID voiceRecordId;
        private String voiceTranscript;
        private String importSource;
        private String sourceReference;
        private String relatedEntityType;
        private UUID relatedEntityId;
        private String relatedEntityLabel;
    }
}
