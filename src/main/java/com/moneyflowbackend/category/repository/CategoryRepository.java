package com.moneyflowbackend.category.repository;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
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
    boolean existsByWorkspaceIdAndCategoryTypeAndNameIgnoreCase(UUID workspaceId, CategoryType categoryType, String name);
    boolean existsByWorkspaceIdAndCategoryTypeAndNameIgnoreCaseAndIdNot(UUID workspaceId, CategoryType categoryType, String name, UUID id);
}
