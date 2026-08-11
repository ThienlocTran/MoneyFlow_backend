package com.moneyflowbackend.planning.repository;

import com.moneyflowbackend.planning.model.ReserveAllocation;
import com.moneyflowbackend.planning.model.ReserveAllocationStatus;
import com.moneyflowbackend.planning.model.ReservePurposeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReserveAllocationRepository extends JpaRepository<ReserveAllocation, UUID> {
    Optional<ReserveAllocation> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    @Query("""
            SELECT r FROM ReserveAllocation r
            LEFT JOIN FETCH r.wallet
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.jar
            WHERE r.workspace.id = :workspaceId
              AND r.deletedAt IS NULL
              AND (:fromTargetDate IS NULL OR r.targetDate >= :fromTargetDate)
              AND (:toTargetDate IS NULL OR r.targetDate <= :toTargetDate)
              AND (:status IS NULL OR r.status = :status)
              AND (:purposeType IS NULL OR r.purposeType = :purposeType)
              AND (:includeInactive = true OR r.status = com.moneyflowbackend.planning.model.ReserveAllocationStatus.ACTIVE)
            ORDER BY CASE WHEN r.targetDate IS NULL THEN 1 ELSE 0 END ASC,
                     r.targetDate ASC,
                     r.createdAt DESC
            """)
    List<ReserveAllocation> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") ReserveAllocationStatus status,
            @Param("purposeType") ReservePurposeType purposeType,
            @Param("includeInactive") boolean includeInactive,
            @Param("fromTargetDate") LocalDate fromTargetDate,
            @Param("toTargetDate") LocalDate toTargetDate,
            Pageable pageable);

    @Query("""
            SELECT r FROM ReserveAllocation r
            LEFT JOIN FETCH r.wallet
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.jar
            WHERE r.workspace.id = :workspaceId
              AND r.deletedAt IS NULL
              AND r.status = com.moneyflowbackend.planning.model.ReserveAllocationStatus.ACTIVE
            ORDER BY CASE WHEN r.targetDate IS NULL THEN 1 ELSE 0 END ASC,
                     r.targetDate ASC,
                     r.createdAt DESC
            """)
    List<ReserveAllocation> findActiveForProjection(@Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT r FROM ReserveAllocation r
            LEFT JOIN FETCH r.wallet
            LEFT JOIN FETCH r.category
            LEFT JOIN FETCH r.jar
            WHERE r.workspace.id = :workspaceId
              AND r.deletedAt IS NULL
            ORDER BY CASE WHEN r.targetDate IS NULL THEN 1 ELSE 0 END ASC,
                     r.targetDate ASC,
                     r.createdAt DESC
            """)
    List<ReserveAllocation> findOverviewReserves(@Param("workspaceId") UUID workspaceId);
}
