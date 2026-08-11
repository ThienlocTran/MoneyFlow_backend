package com.moneyflowbackend.categoryboard.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.categoryboard.dto.CategoryBoardResponse;
import com.moneyflowbackend.categoryboard.dto.CategoryGroupReorderRequest;
import com.moneyflowbackend.categoryboard.dto.CategoryMoveRequest;
import com.moneyflowbackend.categoryboard.dto.JarBoardReorderRequest;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CategoryBoardMutationService {
    private final CategoryRepository categoryRepository;
    private final JarRepository jarRepository;
    private final WorkspaceService workspaceService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final CategoryBoardService categoryBoardService;

    public CategoryBoardMutationService(
            CategoryRepository categoryRepository,
            JarRepository jarRepository,
            WorkspaceService workspaceService,
            WorkspaceMemberRepository workspaceMemberRepository,
            CategoryBoardService categoryBoardService) {
        this.categoryRepository = categoryRepository;
        this.jarRepository = jarRepository;
        this.workspaceService = workspaceService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.categoryBoardService = categoryBoardService;
    }

    @Transactional
    public CategoryBoardResponse moveCategory(UUID workspaceId, UUID categoryId, CategoryMoveRequest req, UUID userId) {
        requireOwner(workspaceId, userId);
        Category category = category(workspaceId, categoryId);
        if (category.isArchived() || !category.isActive()) {
            throw new BusinessException("CATEGORY_ARCHIVED", "Category cannot be moved while archived or inactive", HttpStatus.CONFLICT);
        }
        Jar targetJar = targetJar(workspaceId, req == null ? null : req.targetJarId());
        if (targetJar != null && !targetJar.isActive()) {
            throw new BusinessException("JAR_ARCHIVED", "Jar cannot receive categories while inactive", HttpStatus.CONFLICT);
        }
        Integer targetPosition = req == null ? null : req.targetPosition();
        if (targetPosition != null && targetPosition < 0) {
            throw new BusinessException("CATEGORY_MOVE_INVALID_POSITION", "targetPosition must be zero or greater");
        }

        UUID oldJarId = category.getJar() == null ? null : category.getJar().getId();
        UUID newJarId = targetJar == null ? null : targetJar.getId();
        if (!Objects.equals(oldJarId, newJarId)) {
            normalizeCategoryGroup(workspaceId, oldJarId, category.getId());
        }
        List<Category> targetGroup = activeGroup(workspaceId, newJarId).stream()
                .filter(item -> !item.getId().equals(category.getId()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        int index = targetPosition == null ? targetGroup.size() : Math.min(targetPosition, targetGroup.size());
        category.setJar(targetJar);
        targetGroup.add(index, category);
        saveCategoryOrder(targetGroup);
        return board(workspaceId, Boolean.TRUE.equals(req == null ? null : req.includeStats()), userId);
    }

    @Transactional
    public CategoryBoardResponse reorderCategories(UUID workspaceId, CategoryGroupReorderRequest req, UUID userId) {
        requireOwner(workspaceId, userId);
        UUID jarId = req == null ? null : req.jarId();
        if (jarId != null) {
            Jar jar = jarRepository.findByIdAndWorkspaceId(jarId, workspaceId)
                    .orElseThrow(() -> new BusinessException("JAR_NOT_FOUND", "Jar not found", HttpStatus.NOT_FOUND));
            if (!jar.isActive()) {
                throw new BusinessException("JAR_ARCHIVED", "Jar is inactive", HttpStatus.CONFLICT);
            }
        }
        List<UUID> ids = req == null ? List.of() : req.categoryIds();
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("CATEGORY_BOARD_REORDER_INVALID_PAYLOAD", "categoryIds are required");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException("CATEGORY_REORDER_DUPLICATE_CATEGORY", "Duplicate category in reorder request");
        }
        List<Category> group = activeGroup(workspaceId, jarId);
        Set<UUID> expected = ids(group);
        Set<UUID> requested = new HashSet<>(ids);
        if (!expected.equals(requested)) {
            validateRequestedCategories(workspaceId, jarId, ids);
            throw new BusinessException("CATEGORY_REORDER_INCOMPLETE_GROUP", "Category reorder must include the full group");
        }
        List<Category> ordered = ids.stream().map(id -> group.stream()
                .filter(category -> category.getId().equals(id))
                .findFirst()
                .orElseThrow()).toList();
        saveCategoryOrder(ordered);
        return board(workspaceId, Boolean.TRUE.equals(req.includeStats()), userId);
    }

    @Transactional
    public CategoryBoardResponse reorderJars(UUID workspaceId, JarBoardReorderRequest req, UUID userId) {
        requireOwner(workspaceId, userId);
        List<UUID> ids = req == null ? List.of() : req.jarIds();
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("CATEGORY_BOARD_REORDER_INVALID_PAYLOAD", "jarIds are required");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException("JAR_REORDER_DUPLICATE_JAR", "Duplicate jar in reorder request");
        }
        List<Jar> jars = jarRepository.findAllByWorkspaceIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc(workspaceId);
        Set<UUID> expected = ids(jars);
        Set<UUID> requested = new HashSet<>(ids);
        if (!expected.equals(requested)) {
            if (ids.stream().anyMatch(id -> jarRepository.findByIdAndWorkspaceId(id, workspaceId).isEmpty())) {
                throw new BusinessException("JAR_REORDER_INVALID_JAR", "Jar is missing or inaccessible", HttpStatus.NOT_FOUND);
            }
            throw new BusinessException("JAR_REORDER_INCOMPLETE", "Jar reorder must include all active jars");
        }
        List<Jar> ordered = ids.stream().map(id -> jars.stream()
                .filter(jar -> jar.getId().equals(id))
                .findFirst()
                .orElseThrow()).toList();
        for (int i = 0; i < ordered.size(); i++) {
            Jar jar = ordered.get(i);
            jar.setDisplayOrder(i);
            jar.setUpdatedAt(Instant.now());
        }
        jarRepository.saveAll(ordered);
        return board(workspaceId, Boolean.TRUE.equals(req.includeStats()), userId);
    }

    private WorkspaceMember requireOwner(UUID workspaceId, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .or(() -> workspaceMemberRepository.findByWorkspaceIdAndPersonLinkedUserIdAndMemberStatus(workspaceId, userId, "ACTIVE"))
                .orElseThrow(() -> new BusinessException("FORBIDDEN", "Workspace access denied", HttpStatus.FORBIDDEN));
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new BusinessException("FORBIDDEN", "Only workspace owner can reorder category board", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private Category category(UUID workspaceId, UUID categoryId) {
        return categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
    }

    private Jar targetJar(UUID workspaceId, UUID jarId) {
        if (jarId == null) return null;
        return jarRepository.findByIdAndWorkspaceId(jarId, workspaceId)
                .orElseThrow(() -> new BusinessException("CATEGORY_MOVE_TARGET_JAR_NOT_FOUND", "Target jar not found", HttpStatus.NOT_FOUND));
    }

    private List<Category> activeGroup(UUID workspaceId, UUID jarId) {
        return categoryRepository.findAllByWorkspaceIdOrderByDisplayOrderAsc(workspaceId).stream()
                .filter(category -> category.getCategoryType() == CategoryType.EXPENSE)
                .filter(Category::isActive)
                .filter(category -> !category.isArchived())
                .filter(category -> Objects.equals(category.getJar() == null ? null : category.getJar().getId(), jarId))
                .sorted(this::compareCategories)
                .toList();
    }

    private void validateRequestedCategories(UUID workspaceId, UUID jarId, List<UUID> ids) {
        for (UUID id : ids) {
            Category category = category(workspaceId, id);
            UUID actualJarId = category.getJar() == null ? null : category.getJar().getId();
            if (!category.isActive() || category.isArchived() || category.getCategoryType() != CategoryType.EXPENSE
                    || !Objects.equals(actualJarId, jarId)) {
                throw new BusinessException("CATEGORY_REORDER_GROUP_MISMATCH", "Category is not in the requested group", HttpStatus.CONFLICT);
            }
        }
    }

    private void normalizeCategoryGroup(UUID workspaceId, UUID jarId, UUID excludeCategoryId) {
        List<Category> group = activeGroup(workspaceId, jarId).stream()
                .filter(category -> !category.getId().equals(excludeCategoryId))
                .toList();
        saveCategoryOrder(group);
    }

    private void saveCategoryOrder(List<Category> categories) {
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            category.setDisplayOrder(i);
            category.setUpdatedAt(Instant.now());
        }
        categoryRepository.saveAll(categories);
    }

    private CategoryBoardResponse board(UUID workspaceId, boolean includeStats, UUID userId) {
        return categoryBoardService.getBoard(workspaceId, false, true, true, includeStats, null, null, null, userId);
    }

    private Set<UUID> ids(List<? extends Object> rows) {
        Set<UUID> ids = new HashSet<>();
        for (Object row : rows) {
            if (row instanceof Category category) ids.add(category.getId());
            if (row instanceof Jar jar) ids.add(jar.getId());
        }
        return ids;
    }

    private int compareCategories(Category left, Category right) {
        int order = compareNullable(left.getDisplayOrder(), right.getDisplayOrder());
        if (order != 0) return order;
        order = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER).compare(left.getName(), right.getName());
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
}
