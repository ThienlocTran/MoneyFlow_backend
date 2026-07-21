package com.moneyflowbackend.activity.mapper;

import com.moneyflowbackend.activity.dto.ActivityTimelineItemResponse;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import org.springframework.stereotype.Component;

@Component
public class ActivityTimelineResponseMapper {
    public ActivityTimelineItemResponse toResponse(ActivityCandidate candidate) {
        return ActivityTimelineItemResponse.builder()
                .id(candidate.activityId())
                .occurredAt(candidate.occurredAt())
                .actor(candidate.actor())
                .action(candidate.action())
                .entityType(candidate.entityType())
                .entityId(candidate.entityId())
                .amount(candidate.amount())
                .direction(candidate.direction())
                .businessDate(candidate.businessDate())
                .navigationTarget(candidate.navigationTarget())
                .source(candidate.source())
                .details(candidate.details())
                .build();
    }
}
