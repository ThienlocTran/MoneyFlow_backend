package com.moneyflowbackend.category.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    private UUID id;
    private UUID workspaceId;
    private String name;
    private String type;
    private UUID jarId;
    private String jarName;
    private String icon;
    private boolean isQuickAction;
    private boolean isActive;
    private boolean isArchived;
    private Integer displayOrder;
    private long keywordCount;
    private long usageCount;
    private Instant createdAt;
    private Instant updatedAt;
}
