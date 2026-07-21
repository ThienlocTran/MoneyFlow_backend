package com.moneyflowbackend.income.repository;

import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncomeSourceRepository extends JpaRepository<IncomeSource, UUID> {
    Optional<IncomeSource> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<IncomeSource> findAllByWorkspaceIdAndStatusOrderByNameAsc(UUID workspaceId, IncomeSourceStatus status);

    @Query("""
            SELECT COUNT(i) > 0 FROM IncomeSource i
            WHERE i.workspace.id = :workspaceId
              AND i.status = com.moneyflowbackend.income.model.IncomeSourceStatus.ACTIVE
              AND LOWER(TRIM(i.name)) = LOWER(TRIM(:name))
            """)
    boolean existsActiveNameInWorkspace(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name);

    @Query("""
            SELECT COUNT(i) > 0 FROM IncomeSource i
            WHERE i.workspace.id = :workspaceId
              AND i.status = com.moneyflowbackend.income.model.IncomeSourceStatus.ACTIVE
              AND LOWER(TRIM(i.name)) = LOWER(TRIM(:name))
              AND i.id <> :excludeId
            """)
    boolean existsActiveNameInWorkspaceExcluding(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);
}
