package com.moneyflowbackend.activity.dto;

import com.moneyflowbackend.activity.domain.ActivityAction;
import com.moneyflowbackend.activity.domain.ActivityEntityType;
import com.moneyflowbackend.activity.domain.ActivitySource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ActivityTimelineItemResponse {
    private String id;
    private Instant occurredAt;
    private ActivityActorSummary actor;
    private ActivityAction action;
    private String actionLabel;
    private ActivityEntityType entityType;
    private UUID entityId;
    private String relatedRecordType;
    private UUID relatedRecordId;
    private boolean canOpenDetail;
    private String sourceLabel;
    private BigDecimal amount;
    private String direction;
    private LocalDate businessDate;
    private ActivityNavigationTarget navigationTarget;
    private ActivitySource source;
    private Map<String, Object> details;
}
