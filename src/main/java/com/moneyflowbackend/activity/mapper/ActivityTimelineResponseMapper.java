package com.moneyflowbackend.activity.mapper;

import com.moneyflowbackend.activity.dto.ActivityTimelineItemResponse;
import com.moneyflowbackend.activity.internal.ActivityCandidate;
import org.springframework.stereotype.Component;

@Component
public class ActivityTimelineResponseMapper {
    public ActivityTimelineItemResponse toResponse(ActivityCandidate candidate) {
        boolean canOpenDetail = candidate.navigationTarget().type().name().equals("TRANSACTION")
                && candidate.navigationTarget().entityId() != null;
        return ActivityTimelineItemResponse.builder()
                .id(candidate.activityId())
                .occurredAt(candidate.occurredAt())
                .actor(candidate.actor())
                .action(candidate.action())
                .actionLabel(actionLabel(candidate.action().name()))
                .entityType(candidate.entityType())
                .entityId(candidate.entityId())
                .relatedRecordType(canOpenDetail ? "TRANSACTION" : null)
                .relatedRecordId(canOpenDetail ? candidate.navigationTarget().entityId() : null)
                .canOpenDetail(canOpenDetail)
                .sourceLabel(sourceLabel(candidate.source().name()))
                .amount(candidate.amount())
                .direction(candidate.direction())
                .businessDate(candidate.businessDate())
                .navigationTarget(candidate.navigationTarget())
                .source(candidate.source())
                .details(candidate.details())
                .build();
    }

    private String sourceLabel(String value) {
        return switch (value) {
            case "TRANSACTION_AUDIT" -> "Lịch sử giao dịch";
            case "TRANSACTION" -> "Giao dịch";
            case "DAILY_CLOSING" -> "Chốt ngày";
            default -> value;
        };
    }

    private String actionLabel(String value) {
        return switch (value) {
            case "TRANSACTION_CREATED" -> "Tạo giao dịch";
            case "TRANSACTION_UPDATED" -> "Cập nhật giao dịch";
            case "TRANSACTION_VOIDED" -> "Xóa giao dịch";
            case "TRANSACTION_RESTORED" -> "Khôi phục giao dịch";
            case "TRANSFER_CREATED" -> "Tạo chuyển ví";
            case "ADJUSTMENT_CREATED" -> "Tạo điều chỉnh";
            case "OBLIGATION_CONFIRMED" -> "Xác nhận nghĩa vụ";
            default -> value;
        };
    }
}
