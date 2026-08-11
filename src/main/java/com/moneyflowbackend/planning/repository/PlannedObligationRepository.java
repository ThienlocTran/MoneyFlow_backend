package com.moneyflowbackend.planning.repository;

import com.moneyflowbackend.planning.model.PlannedObligation;
import com.moneyflowbackend.planning.model.PlannedObligationPriority;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedObligationRepository extends JpaRepository<PlannedObligation, UUID> {
    Optional<PlannedObligation> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);
    boolean existsByWorkspaceIdAndLinkedTransactionIdAndDeletedAtIsNull(UUID workspaceId, UUID linkedTransactionId);

    @Query("""
            SELECT o FROM PlannedObligation o
            LEFT JOIN FETCH o.wallet
            LEFT JOIN FETCH o.category c
            LEFT JOIN FETCH c.jar
            WHERE o.workspace.id = :workspaceId
              AND o.deletedAt IS NULL
              AND (:from IS NULL OR o.dueDate >= :from)
              AND (:to IS NULL OR o.dueDate <= :to)
              AND (:status IS NULL OR o.status = :status)
              AND (:priority IS NULL OR o.priority = :priority)
              AND (:includeCancelled = true OR o.status <> com.moneyflowbackend.planning.model.PlannedObligationStatus.CANCELLED)
            ORDER BY o.dueDate ASC,
                     CASE o.priority
                       WHEN com.moneyflowbackend.planning.model.PlannedObligationPriority.REQUIRED THEN 0
                       WHEN com.moneyflowbackend.planning.model.PlannedObligationPriority.IMPORTANT THEN 1
                       ELSE 2
                     END ASC,
                     o.createdAt ASC
            """)
    List<PlannedObligation> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") PlannedObligationStatus status,
            @Param("priority") PlannedObligationPriority priority,
            @Param("includeCancelled") boolean includeCancelled,
            Pageable pageable);

    @Query("""
            SELECT o FROM PlannedObligation o
            WHERE o.workspace.id = :workspaceId
              AND o.deletedAt IS NULL
              AND o.status = com.moneyflowbackend.planning.model.PlannedObligationStatus.PLANNED
              AND o.priority IN (
                com.moneyflowbackend.planning.model.PlannedObligationPriority.REQUIRED,
                com.moneyflowbackend.planning.model.PlannedObligationPriority.IMPORTANT
              )
              AND o.dueDate <= :to
            ORDER BY o.dueDate ASC, o.createdAt ASC
            """)
    List<PlannedObligation> findProjectionObligations(
            @Param("workspaceId") UUID workspaceId,
            @Param("to") LocalDate to);

    @Query("""
            SELECT o FROM PlannedObligation o
            LEFT JOIN FETCH o.wallet
            LEFT JOIN FETCH o.category
            WHERE o.workspace.id = :workspaceId
              AND o.deletedAt IS NULL
              AND o.dueDate <= :to
            ORDER BY o.dueDate ASC, o.createdAt ASC
            """)
    List<PlannedObligation> findOverviewObligations(
            @Param("workspaceId") UUID workspaceId,
            @Param("to") LocalDate to);
}
