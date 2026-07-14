package com.moneyflowbackend.category.repository;

import com.moneyflowbackend.category.model.CategoryKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryKeywordRepository extends JpaRepository<CategoryKeyword, UUID> {
    List<CategoryKeyword> findAllByWorkspaceIdAndCategoryIdOrderByPriorityDescKeywordAsc(UUID workspaceId, UUID categoryId);
    List<CategoryKeyword> findAllByWorkspaceIdOrderByPriorityDescKeywordAsc(UUID workspaceId);
    Optional<CategoryKeyword> findByIdAndWorkspaceIdAndCategoryId(UUID id, UUID workspaceId, UUID categoryId);
    Optional<CategoryKeyword> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    boolean existsByWorkspaceIdAndKeywordIgnoreCase(UUID workspaceId, String keyword);

    @Query("""
            SELECT COUNT(k) > 0 FROM CategoryKeyword k
            WHERE k.workspace.id = :workspaceId
              AND LOWER(k.keyword) = LOWER(:keyword)
              AND k.id <> :excludeId
            """)
    boolean existsKeywordExcluding(
            @Param("workspaceId") UUID workspaceId,
            @Param("keyword") String keyword,
            @Param("excludeId") UUID excludeId);

    long countByCategoryId(UUID categoryId);

    @Query("""
            SELECT k.category.id, COUNT(k) FROM CategoryKeyword k
            WHERE k.category.id IN :categoryIds
            GROUP BY k.category.id
            """)
    List<Object[]> countByCategoryIds(@Param("categoryIds") Collection<UUID> categoryIds);
}
