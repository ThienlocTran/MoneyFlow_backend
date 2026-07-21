package com.moneyflowbackend.category.service;

import com.moneyflowbackend.category.dto.CategoryReorderRequest;
import com.moneyflowbackend.category.dto.CategoryRequest;
import com.moneyflowbackend.category.dto.CategoryResponse;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.jar.model.Jar;
import com.moneyflowbackend.jar.repository.JarRepository;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final JarRepository jarRepository;
    private final CategoryKeywordRepository keywordRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final TransactionRepository transactionRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            JarRepository jarRepository,
            CategoryKeywordRepository keywordRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.jarRepository = jarRepository;
        this.keywordRepository = keywordRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(
            UUID workspaceId,
            String type,
            UUID jarId,
            Boolean active,
            Boolean archived,
            Boolean quickAction,
            boolean includeInactive,
            boolean includeArchived,
            UUID userId) {
        requireActiveMember(workspaceId, userId);
        CategoryType parsedType = type == null || type.isBlank() ? null : parseType(type);
        List<Category> categories = categoryRepository.findList(
                workspaceId, parsedType, jarId, active, archived, quickAction, includeInactive, includeArchived);
        return mapToResponses(workspaceId, categories);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID workspaceId, UUID categoryId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        return mapToResponse(findCategoryInWorkspace(workspaceId, categoryId));
    }

    @Transactional
    public CategoryResponse create(UUID workspaceId, CategoryRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));

        CategoryType type = parseType(req.getType());
        if (type == CategoryType.SPECIAL) {
            throw new BusinessException("INVALID_CATEGORY_TYPE", "Special categories cannot be created");
        }

        String name = normalizeName(req.getName());
        if (categoryRepository.existsByWorkspaceIdAndCategoryTypeAndNameIgnoreCase(workspaceId, type, name)) {
            throw new BusinessException("CATEGORY_NAME_ALREADY_EXISTS", "Category name already exists");
        }

        Jar jar = resolveJarForCategory(workspaceId, type, req.getJarId());
        validateDefaultSpendingScope(type, req.getDefaultSpendingScope());
        boolean active = req.getIsActive() == null || req.getIsActive();
        boolean quickAction = Boolean.TRUE.equals(req.getIsQuickAction());
        if (quickAction && !active) {
            throw new BusinessException("CATEGORY_CANNOT_BE_QUICK_ACTION", "Inactive category cannot be quick action");
        }

        Category category = Category.builder()
                .workspace(workspace)
                .jar(jar)
                .name(name)
                .categoryType(type)
                .defaultSpendingScope(req.getDefaultSpendingScope())
                .icon(req.getIcon())
                .isActive(active)
                .isArchived(false)
                .isQuickAction(quickAction)
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : nextDisplayOrder(workspaceId))
                .build();

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID workspaceId, UUID categoryId, CategoryRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Category category = findCategoryInWorkspace(workspaceId, categoryId);

        CategoryType type = parseType(req.getType());
        validateTypeChange(workspaceId, category, type);

        String name = normalizeName(req.getName());
        if (categoryRepository.existsByWorkspaceIdAndCategoryTypeAndNameIgnoreCaseAndIdNot(workspaceId, type, name, categoryId)) {
            throw new BusinessException("CATEGORY_NAME_ALREADY_EXISTS", "Category name already exists");
        }

        Jar jar = resolveJarForCategory(workspaceId, type, req.getJarId());
        validateDefaultSpendingScope(type, req.getDefaultSpendingScope());
        boolean active = req.getIsActive() == null ? category.isActive() : req.getIsActive();
        boolean archived = category.isArchived();
        boolean quickAction = req.getIsQuickAction() == null ? category.isQuickAction() : req.getIsQuickAction();
        if (archived) {
            active = false;
            quickAction = false;
        }
        if (quickAction && (!active || archived)) {
            throw new BusinessException("CATEGORY_CANNOT_BE_QUICK_ACTION", "Inactive or archived category cannot be quick action");
        }

        category.setName(name);
        category.setCategoryType(type);
        category.setDefaultSpendingScope(req.getDefaultSpendingScope());
        category.setJar(jar);
        category.setIcon(req.getIcon());
        category.setActive(active);
        category.setQuickAction(quickAction);
        if (req.getDisplayOrder() != null) {
            category.setDisplayOrder(req.getDisplayOrder());
        }
        category.setUpdatedAt(Instant.now());

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional
    public void toggleStatus(UUID workspaceId, UUID categoryId, boolean active, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Category category = findCategoryInWorkspace(workspaceId, categoryId);
        category.setActive(active);
        if (!active) {
            category.setQuickAction(false);
        }
        category.setUpdatedAt(Instant.now());
        categoryRepository.save(category);
    }

    @Transactional
    public void toggleArchived(UUID workspaceId, UUID categoryId, boolean archived, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Category category = findCategoryInWorkspace(workspaceId, categoryId);
        category.setArchived(archived);
        if (archived) {
            category.setActive(false);
            category.setQuickAction(false);
        }
        category.setUpdatedAt(Instant.now());
        categoryRepository.save(category);
    }

    @Transactional
    public void toggleQuickAction(UUID workspaceId, UUID categoryId, boolean quickAction, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Category category = findCategoryInWorkspace(workspaceId, categoryId);
        if (quickAction && (!category.isActive() || category.isArchived())) {
            throw new BusinessException("CATEGORY_CANNOT_BE_QUICK_ACTION", "Inactive or archived category cannot be quick action");
        }
        category.setQuickAction(quickAction);
        category.setUpdatedAt(Instant.now());
        categoryRepository.save(category);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID categoryId, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Category category = findCategoryInWorkspace(workspaceId, categoryId);
        if (transactionRepository.countByWorkspaceIdAndCategoryId(workspaceId, categoryId) > 0) {
            throw new BusinessException("CATEGORY_IN_USE", "Category has transactions; deactivate it instead");
        }
        categoryRepository.delete(category);
    }

    @Transactional
    public List<CategoryResponse> reorder(UUID workspaceId, CategoryReorderRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Set<UUID> seen = new HashSet<>();
        for (CategoryReorderRequest.Item item : req.getItems()) {
            if (!seen.add(item.getCategoryId())) {
                throw new BusinessException("INVALID_REORDER_REQUEST", "Duplicate category in reorder request");
            }
            Category category = findCategoryInWorkspace(workspaceId, item.getCategoryId());
            category.setDisplayOrder(item.getDisplayOrder());
            category.setUpdatedAt(Instant.now());
            categoryRepository.save(category);
        }
        return list(workspaceId, null, null, null, null, null, true, true, userId);
    }

    private void validateTypeChange(UUID workspaceId, Category category, CategoryType newType) {
        CategoryType currentType = category.getCategoryType();
        if (currentType == newType) {
            return;
        }
        if (currentType == CategoryType.SPECIAL || newType == CategoryType.SPECIAL) {
            throw new BusinessException("INVALID_CATEGORY_TYPE", "Special category type cannot be changed");
        }
        if (transactionRepository.countByWorkspaceIdAndCategoryId(workspaceId, category.getId()) > 0) {
            throw new BusinessException("CATEGORY_TYPE_CHANGE_NOT_ALLOWED", "Category type cannot be changed after transactions exist");
        }
    }

    private Jar resolveJarForCategory(UUID workspaceId, CategoryType type, UUID jarId) {
        if (type == CategoryType.INCOME) {
            if (jarId != null) {
                throw new BusinessException("INCOME_CATEGORY_CANNOT_HAVE_JAR", "Income category cannot have jar");
            }
            return null;
        }
        if (jarId == null) {
            return null;
        }
        Jar jar = jarRepository.findByIdAndWorkspaceId(jarId, workspaceId)
                .orElseThrow(() -> new BusinessException("JAR_NOT_FOUND", "Jar not found", HttpStatus.NOT_FOUND));
        if (!jar.isActive()) {
            throw new BusinessException("JAR_INACTIVE", "Jar is inactive");
        }
        return jar;
    }

    private void validateDefaultSpendingScope(CategoryType type, SpendingScope defaultSpendingScope) {
        if (type != CategoryType.EXPENSE && defaultSpendingScope != null) {
            throw new BusinessException(
                    "INVALID_CATEGORY_SPENDING_SCOPE",
                    "Default spending scope is only supported for expense categories.");
        }
    }

    private Category findCategoryInWorkspace(UUID workspaceId, UUID categoryId) {
        return categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private WorkspaceMember requireWritableMember(UUID workspaceId, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new BusinessException("FORBIDDEN", "Only workspace owner can modify categories", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private CategoryType parseType(String type) {
        try {
            return CategoryType.valueOf(type == null ? "" : type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("INVALID_CATEGORY_TYPE", "Invalid category type");
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "Category name is required");
        }
        return normalized;
    }

    private int nextDisplayOrder(UUID workspaceId) {
        return categoryRepository.findAllByWorkspaceIdOrderByDisplayOrderAsc(workspaceId).stream()
                .map(Category::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private List<CategoryResponse> mapToResponses(UUID workspaceId, List<Category> categories) {
        List<UUID> categoryIds = categories.stream().map(Category::getId).toList();
        Map<UUID, Long> keywordCounts = countMap(categoryIds.isEmpty() ? List.of() : keywordRepository.countByCategoryIds(categoryIds));
        Map<UUID, Long> usageCounts = countMap(categoryIds.isEmpty() ? List.of() : transactionRepository.countByWorkspaceIdAndCategoryIds(workspaceId, categoryIds));
        return categories.stream()
                .map(category -> mapToResponse(
                        category,
                        keywordCounts.getOrDefault(category.getId(), 0L),
                        usageCounts.getOrDefault(category.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private Map<UUID, Long> countMap(List<Object[]> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private CategoryResponse mapToResponse(Category category) {
        return mapToResponse(
                category,
                keywordRepository.countByCategoryId(category.getId()),
                transactionRepository.countByWorkspaceIdAndCategoryId(category.getWorkspace().getId(), category.getId()));
    }

    private CategoryResponse mapToResponse(Category category, long keywordCount, long usageCount) {
        return CategoryResponse.builder()
                .id(category.getId())
                .workspaceId(category.getWorkspace().getId())
                .name(category.getName())
                .type(category.getCategoryType().name())
                .jarId(category.getJar() != null ? category.getJar().getId() : null)
                .jarName(category.getJar() != null ? category.getJar().getName() : null)
                .defaultSpendingScope(category.getDefaultSpendingScope())
                .icon(category.getIcon())
                .isQuickAction(category.isQuickAction())
                .isActive(category.isActive())
                .isArchived(category.isArchived())
                .displayOrder(category.getDisplayOrder())
                .keywordCount(keywordCount)
                .usageCount(usageCount)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
