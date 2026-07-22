package com.moneyflowbackend.income.repository;

import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncomeSourceRepository extends JpaRepository<IncomeSource, UUID> {
    Optional<IncomeSource> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<IncomeSource> findAllByWorkspaceIdAndStatusOrderByNameAsc(UUID workspaceId, IncomeSourceStatus status);

    @Query("""
            SELECT i FROM IncomeSource i
            WHERE i.workspace.id = :workspaceId
              AND i.status = :status
            ORDER BY LOWER(i.name) ASC, i.id ASC
            """)
    List<IncomeSource> findAllForList(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") IncomeSourceStatus status);

    @Query("""
            SELECT i FROM IncomeSource i
            WHERE i.workspace.id = :workspaceId
              AND i.status = :status
              AND LOWER(i.name) LIKE :search
            ORDER BY LOWER(i.name) ASC, i.id ASC
            """)
    List<IncomeSource> searchForList(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") IncomeSourceStatus status,
            @Param("search") String search);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM IncomeSource i
            WHERE i.id = :id
              AND i.workspace.id = :workspaceId
            """)
    Optional<IncomeSource> findByIdAndWorkspaceIdForUpdate(
            @Param("id") UUID id,
            @Param("workspaceId") UUID workspaceId);

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
