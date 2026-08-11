package com.moneyflowbackend.categoryboard.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.categoryboard.dto.CategoryBoardItemResponse;
import com.moneyflowbackend.categoryboard.dto.CategoryBoardResponse;
import com.moneyflowbackend.categoryboard.dto.JarBoardGroupResponse;
import com.moneyflowbackend.categoryboard.dto.UncategorizedGroupResponse;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CategoryBoardService {
    private static final String UNCATEGORIZED = "UNCATEGORIZED";
    private static final String UNCATEGORIZED_NAME = "Chưa gắn hũ";

    private final JarRepository jarRepository;
    private final CategoryRepository categoryRepository;
    private final WorkspaceService workspaceService;

    public CategoryBoardService(
            JarRepository jarRepository,
            CategoryRepository categoryRepository,
            WorkspaceService workspaceService) {
        this.jarRepository = jarRepository;
        this.categoryRepository = categoryRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional(readOnly = true)
    public CategoryBoardResponse getBoard(
            UUID workspaceId,
            boolean includeArchived,
            boolean includeEmptyJars,
            boolean includeUncategorized,
            boolean includeStats,
            UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);

        List<Jar> jars = includeArchived
                ? jarRepository.findAllByWorkspaceIdOrderByDisplayOrderAscNameAsc(workspaceId)
                : jarRepository.findAllByWorkspaceIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc(workspaceId);
        Map<UUID, Jar> visibleJars = new LinkedHashMap<>();
        for (Jar jar : jars.stream().sorted(this::compareJars).toList()) {
            visibleJars.put(jar.getId(), jar);
        }

        List<Category> categories = categoryRepository.findList(
                workspaceId, null, null, null, null, null, includeArchived, includeArchived)
                .stream()
                .sorted(this::compareCategories)
                .toList();

        Map<UUID, List<CategoryBoardItemResponse>> grouped = new LinkedHashMap<>();
        List<CategoryBoardItemResponse> uncategorized = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (includeStats) {
            warnings.add("CATEGORY_BOARD_STATS_NOT_IMPLEMENTED");
        }
        warnings.add("HISTORICAL_JAR_SNAPSHOT_UNAVAILABLE");

        for (Category category : categories) {
            Jar jar = category.getJar();
            if (jar != null && visibleJars.containsKey(jar.getId())) {
                grouped.computeIfAbsent(jar.getId(), ignored -> new ArrayList<>()).add(item(category, jar));
            } else {
                if (jar != null) {
                    addOnce(warnings, "CATEGORY_BOARD_PARTIAL_DATA");
                }
                uncategorized.add(item(category, null));
            }
        }
        if (!uncategorized.isEmpty()) {
            addOnce(warnings, "CATEGORY_UNCATEGORIZED_EXISTS");
        }

        List<JarBoardGroupResponse> groups = new ArrayList<>();
        for (Jar jar : visibleJars.values()) {
            List<CategoryBoardItemResponse> items = grouped.getOrDefault(jar.getId(), List.of());
            if (includeEmptyJars || !items.isEmpty()) {
                groups.add(new JarBoardGroupResponse(
                        jar.getId(),
                        jar.getName(),
                        null,
                        null,
                        null,
                        jar.getDisplayOrder(),
                        jar.isActive() ? "ACTIVE" : "INACTIVE",
                        items.size(),
                        items));
            }
        }

        UncategorizedGroupResponse uncategorizedGroup = includeUncategorized
                ? new UncategorizedGroupResponse(UNCATEGORIZED, UNCATEGORIZED_NAME, uncategorized.size(), uncategorized)
                : null;

        return new CategoryBoardResponse(
                workspaceId,
                Instant.now(),
                includeArchived,
                includeEmptyJars,
                includeUncategorized,
                groups,
                uncategorizedGroup,
                warnings);
    }

    private CategoryBoardItemResponse item(Category category, Jar visibleJar) {
        boolean active = category.isActive() && !category.isArchived();
        return new CategoryBoardItemResponse(
                category.getId(),
                category.getName(),
                category.getCategoryType().name(),
                null,
                category.getIcon(),
                category.getDisplayOrder(),
                category.isArchived() ? "ARCHIVED" : category.isActive() ? "ACTIVE" : "INACTIVE",
                visibleJar == null ? null : visibleJar.getId(),
                visibleJar == null ? null : visibleJar.getName(),
                active,
                active,
                false,
                List.of());
    }

    private int compareJars(Jar left, Jar right) {
        int order = compareNullable(left.getDisplayOrder(), right.getDisplayOrder());
        if (order != 0) return order;
        order = compareString(left.getName(), right.getName());
        if (order != 0) return order;
        order = compareNullable(left.getCreatedAt(), right.getCreatedAt());
        if (order != 0) return order;
        return left.getId().compareTo(right.getId());
    }

    private int compareCategories(Category left, Category right) {
        int order = compareNullable(left.getDisplayOrder(), right.getDisplayOrder());
        if (order != 0) return order;
        order = compareString(left.getName(), right.getName());
        if (order != 0) return order;
        order = compareNullable(left.getCreatedAt(), right.getCreatedAt());
        if (order != 0) return order;
        return left.getId().compareTo(right.getId());
    }

    private <T extends Comparable<T>> int compareNullable(T left, T right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private int compareString(String left, String right) {
        return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER).compare(left, right);
    }

    private void addOnce(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }
}
