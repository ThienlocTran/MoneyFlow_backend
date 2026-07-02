package com.moneyflowbackend.jar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JarResponse {
    private UUID id;
    private UUID workspaceId;
    private String code;
    private String name;
    private BigDecimal allocationPercent;
    private Integer displayOrder;
    private boolean isActive;
    private long categoryCount;
    private long usageCount;
    private Instant createdAt;
    private Instant updatedAt;
}
