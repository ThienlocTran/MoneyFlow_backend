package com.moneyflowbackend.category.service;

import com.moneyflowbackend.category.dto.CategoryKeywordRequest;
import com.moneyflowbackend.category.dto.CategoryKeywordResponse;
import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryKeywordService {

    private final CategoryKeywordRepository keywordRepository;
    private final CategoryRepository categoryRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public CategoryKeywordService(
            CategoryKeywordRepository keywordRepository,
            CategoryRepository categoryRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.keywordRepository = keywordRepository;
        this.categoryRepository = categoryRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryKeywordResponse> list(UUID workspaceId, UUID categoryId, UUID userId) {
        requireActiveMember(workspaceId, userId);
        findCategoryInWorkspace(workspaceId, categoryId);
        return keywordRepository.findAllByWorkspaceIdAndCategoryIdOrderByPriorityDescKeywordAsc(workspaceId, categoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryKeywordResponse create(UUID workspaceId, UUID categoryId, CategoryKeywordRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        Category category = findCategoryInWorkspace(workspaceId, categoryId);
        requireUsableCategory(category);
        String keyword = normalizeKeyword(req.getKeyword());
        if (keywordRepository.existsByWorkspaceIdAndKeywordIgnoreCase(workspaceId, keyword)) {
            throw new BusinessException("KEYWORD_ALREADY_EXISTS", "Keyword already exists");
        }

        CategoryKeyword categoryKeyword = CategoryKeyword.builder()
                .workspace(category.getWorkspace())
                .category(category)
                .keyword(keyword)
                .priority(req.getPriority() != null ? req.getPriority() : 0)
                .isUserLearned(req.getIsUserLearned() == null || req.getIsUserLearned())
                .build();

        return mapToResponse(keywordRepository.save(categoryKeyword));
    }

    @Transactional
    public CategoryKeywordResponse update(UUID workspaceId, UUID categoryId, UUID keywordId, CategoryKeywordRequest req, UUID userId) {
        requireWritableMember(workspaceId, userId);
        findCategoryInWorkspace(workspaceId, categoryId);
        CategoryKeyword categoryKeyword = findKeywordInCategory(workspaceId, categoryId, keywordId);
        String keyword = normalizeKeyword(req.getKeyword());
        if (keywordRepository.existsKeywordExcluding(workspaceId, keyword, keywordId)) {
            throw new BusinessException("KEYWORD_ALREADY_EXISTS", "Keyword already exists");
        }

        categoryKeyword.setKeyword(keyword);
        categoryKeyword.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        if (req.getIsUserLearned() != null) {
            categoryKeyword.setUserLearned(req.getIsUserLearned());
        }

        return mapToResponse(keywordRepository.save(categoryKeyword));
    }

    @Transactional
    public void delete(UUID workspaceId, UUID categoryId, UUID keywordId, UUID userId) {
        requireWritableMember(workspaceId, userId);
        findCategoryInWorkspace(workspaceId, categoryId);
        CategoryKeyword categoryKeyword = findKeywordInCategory(workspaceId, categoryId, keywordId);
        keywordRepository.delete(categoryKeyword);
    }

    private Category findCategoryInWorkspace(UUID workspaceId, UUID categoryId) {
        return categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Category not found", HttpStatus.NOT_FOUND));
    }

    private CategoryKeyword findKeywordInCategory(UUID workspaceId, UUID categoryId, UUID keywordId) {
        return keywordRepository.findByIdAndWorkspaceIdAndCategoryId(keywordId, workspaceId, categoryId)
                .orElseGet(() -> {
                    keywordRepository.findByIdAndWorkspaceId(keywordId, workspaceId)
                            .ifPresent(keyword -> {
                                throw new BusinessException("KEYWORD_CATEGORY_MISMATCH", "Keyword does not belong to category");
                            });
                    throw new BusinessException("KEYWORD_NOT_FOUND", "Keyword not found", HttpStatus.NOT_FOUND);
                });
    }

    private void requireUsableCategory(Category category) {
        if (category.isArchived()) {
            throw new BusinessException("CATEGORY_ARCHIVED", "Category is archived");
        }
        if (!category.isActive()) {
            throw new BusinessException("CATEGORY_INACTIVE", "Category is inactive");
        }
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
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException("FORBIDDEN", "Viewer cannot modify keywords", HttpStatus.FORBIDDEN);
        }
        return member;
    }

    private String normalizeKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new BusinessException("INVALID_KEYWORD", "Keyword is required");
        }
        return normalized;
    }

    private CategoryKeywordResponse mapToResponse(CategoryKeyword keyword) {
        return CategoryKeywordResponse.builder()
                .id(keyword.getId())
                .categoryId(keyword.getCategory().getId())
                .keyword(keyword.getKeyword())
                .priority(keyword.getPriority())
                .isUserLearned(keyword.isUserLearned())
                .build();
    }
}
