package com.moneyflowbackend.sinkingfund.repository;

import com.moneyflowbackend.sinkingfund.model.SinkingFund;
import com.moneyflowbackend.sinkingfund.model.SinkingFundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SinkingFundRepository extends JpaRepository<SinkingFund, UUID> {
    Optional<SinkingFund> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<SinkingFund> findAllByWorkspaceIdAndStatus(UUID workspaceId, SinkingFundStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT f FROM SinkingFund f
            WHERE f.id = :id
              AND f.workspace.id = :workspaceId
            """)
    Optional<SinkingFund> findByIdAndWorkspaceIdForUpdate(
            @Param("id") UUID id,
            @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT COUNT(f) > 0 FROM SinkingFund f
            WHERE f.workspace.id = :workspaceId
              AND f.status <> com.moneyflowbackend.sinkingfund.model.SinkingFundStatus.ARCHIVED
              AND LOWER(TRIM(f.name)) = LOWER(TRIM(:name))
            """)
    boolean existsOpenNameInWorkspace(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name);

    @Query("""
            SELECT COUNT(f) > 0 FROM SinkingFund f
            WHERE f.workspace.id = :workspaceId
              AND f.status <> com.moneyflowbackend.sinkingfund.model.SinkingFundStatus.ARCHIVED
              AND LOWER(TRIM(f.name)) = LOWER(TRIM(:name))
              AND f.id <> :excludeId
            """)
    boolean existsOpenNameInWorkspaceExcluding(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);
}
