package com.moneyflowbackend.categoryboard.dto;

import java.util.UUID;

public record CategoryMoveRequest(
        UUID targetJarId,
        Integer targetPosition,
        Boolean includeStats) {
}
