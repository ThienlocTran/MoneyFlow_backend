package com.moneyflowbackend.transaction.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransactionAuditLogRepository extends JpaRepository<TransactionAuditLog, UUID> {
    List<TransactionAuditLog> findByWorkspaceIdAndTransactionIdOrderByCreatedAtAsc(UUID workspaceId, UUID transactionId);

    @Query("""
            SELECT l.id AS id,
                   l.createdAt AS occurredAt,
                   l.action AS auditAction,
                   tx.id AS transactionId,
                   actor.id AS actorUserId,
                   actor.fullName AS actorDisplayName
            FROM TransactionAuditLog l
            LEFT JOIN l.transaction tx
            LEFT JOIN l.actorUser actor
            WHERE l.workspace.id = :workspaceId
              AND (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt <= :to)
              AND (:actorId IS NULL OR actor.id = :actorId)
              AND (
                    :cursorOccurredAt IS NULL
                    OR l.createdAt < :cursorOccurredAt
                    OR (l.createdAt = :cursorOccurredAt AND l.id < :cursorId)
              )
            ORDER BY l.createdAt DESC, l.id DESC
            """)
    List<TransactionAuditTimelineProjection> findTimelinePage(
            @Param("workspaceId") UUID workspaceId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("actorId") UUID actorId,
            @Param("cursorOccurredAt") Instant cursorOccurredAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
