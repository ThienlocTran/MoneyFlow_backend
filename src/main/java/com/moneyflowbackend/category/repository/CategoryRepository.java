package com.moneyflowbackend.category.repository;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByWorkspaceIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID workspaceId);
    List<Category> findAllByWorkspaceIdOrderByDisplayOrderAsc(UUID workspaceId);
    Optional<Category> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    long countByWorkspaceId(UUID workspaceId);
    long countByWorkspaceIdAndJarId(UUID workspaceId, UUID jarId);
    long countByWorkspaceIdAndJarIdAndIsActiveTrue(UUID workspaceId, UUID jarId);
    long countByWorkspaceIdAndCategoryTypeAndJarIsNotNullAndIsActiveTrue(UUID workspaceId, CategoryType categoryType);
    long countByWorkspaceIdAndCategoryTypeAndJarIsNullAndIsActiveTrueAndIsArchivedFalse(UUID workspaceId, CategoryType categoryType);

    @Query("""
            SELECT COUNT(c) FROM Category c
            WHERE c.workspace.id = :workspaceId
              AND c.categoryType = :categoryType
              AND c.jar IS NULL
              AND (c.isActive = false OR c.isArchived = true)
            """)
    long countInactiveOrArchivedUnmapped(
            @Param("workspaceId") UUID workspaceId,
            @Param("categoryType") CategoryType categoryType);

    @Query("""
            SELECT c FROM Category c
            LEFT JOIN FETCH c.jar
            WHERE c.workspace.id = :workspaceId
              AND (:includeInactive = true OR c.isActive = true)
              AND (:includeArchived = true OR c.isArchived = false)
              AND (:categoryType IS NULL OR c.categoryType = :categoryType)
              AND (:jarId IS NULL OR c.jar.id = :jarId)
              AND (:active IS NULL OR c.isActive = :active)
              AND (:archived IS NULL OR c.isArchived = :archived)
              AND (:quickAction IS NULL OR c.isQuickAction = :quickAction)
            ORDER BY c.displayOrder ASC
            """)
    List<Category> findList(
            @Param("workspaceId") UUID workspaceId,
            @Param("categoryType") CategoryType categoryType,
            @Param("jarId") UUID jarId,
            @Param("active") Boolean active,
            @Param("archived") Boolean archived,
            @Param("quickAction") Boolean quickAction,
            @Param("includeInactive") boolean includeInactive,
            @Param("includeArchived") boolean includeArchived);

    boolean existsByWorkspaceIdAndCategoryTypeAndNameIgnoreCase(UUID workspaceId, CategoryType categoryType, String name);
    boolean existsByWorkspaceIdAndCategoryTypeAndNameIgnoreCaseAndIdNot(UUID workspaceId, CategoryType categoryType, String name, UUID id);
}
