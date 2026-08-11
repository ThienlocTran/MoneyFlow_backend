package com.moneyflowbackend.categoryboard.dto;

import java.util.List;
import java.util.UUID;

public record CategoryBoardItemResponse(
        UUID categoryId,
        String name,
        String type,
        String color,
        String icon,
        Integer displayOrder,
        String status,
        UUID jarId,
        String jarName,
        boolean canMove,
        boolean canArchive,
        boolean canDelete,
        List<String> warnings) {
}
