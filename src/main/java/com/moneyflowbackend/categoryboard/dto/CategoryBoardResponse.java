package com.moneyflowbackend.categoryboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CategoryBoardResponse(
        UUID workspaceId,
        Instant generatedAt,
        boolean includeArchived,
        boolean includeEmptyJars,
        boolean includeUncategorized,
        List<JarBoardGroupResponse> jars,
        UncategorizedGroupResponse uncategorizedGroup,
        List<String> warnings) {
}
