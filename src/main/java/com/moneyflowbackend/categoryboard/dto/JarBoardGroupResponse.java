package com.moneyflowbackend.categoryboard.dto;

import java.util.List;
import java.util.UUID;

public record JarBoardGroupResponse(
        UUID jarId,
        String name,
        String description,
        String color,
        String icon,
        Integer displayOrder,
        String status,
        long categoryCount,
        List<CategoryBoardItemResponse> categories) {
}
