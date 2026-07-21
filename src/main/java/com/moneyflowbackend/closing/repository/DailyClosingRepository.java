package com.moneyflowbackend.closing.repository;

import com.moneyflowbackend.closing.model.DailyClosing;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyClosingRepository extends JpaRepository<DailyClosing, UUID> {
    interface ActivityDailyClosingContext {
        UUID getId();
        UUID getWorkspaceId();
        LocalDate getClosingDate();
        Instant getCompletedAt();
        UUID getCompletedByUserId();
        String getCompletedByDisplayName();
        long getSnapshotCount();
    }

    Optional<DailyClosing> findByWorkspaceIdAndClosingDate(UUID workspaceId, LocalDate closingDate);
    boolean existsByWorkspaceIdAndClosingDate(UUID workspaceId, LocalDate closingDate);

    @Query("""
            SELECT d.id AS id,
                   workspace.id AS workspaceId,
                   d.closingDate AS closingDate,
                   d.completedAt AS completedAt,
                   completedBy.id AS completedByUserId,
                   completedBy.fullName AS completedByDisplayName,
                   (
                       SELECT COUNT(snapshot)
                       FROM WalletBalanceSnapshot snapshot
                       WHERE snapshot.workspace.id = :workspaceId
                         AND snapshot.dailyClosing.id = d.id
                   ) AS snapshotCount
            FROM DailyClosing d
            JOIN d.workspace workspace
            LEFT JOIN d.completedBy completedBy
            WHERE workspace.id = :workspaceId
              AND d.status = com.moneyflowbackend.closing.model.DailyClosingStatus.COMPLETED
              AND d.completedAt IS NOT NULL
              AND d.completedAt >= :from
              AND d.completedAt < :to
              AND (:actorId IS NULL OR completedBy.id = :actorId)
              AND (
                    d.completedAt < :cursorOccurredAt
                    OR (
                        d.completedAt = :cursorOccurredAt
                        AND (
                            :sourceRank < :cursorSourceRank
                            OR (:sourceRank = :cursorSourceRank AND d.id < :cursorId)
                        )
                    )
              )
            ORDER BY d.completedAt DESC, d.id DESC
            """)
    List<ActivityDailyClosingContext> findActivityContextPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("actorId") UUID actorId,
            @Param("cursorOccurredAt") Instant cursorOccurredAt,
            @Param("cursorSourceRank") Integer cursorSourceRank,
            @Param("cursorId") UUID cursorId,
            @Param("sourceRank") int sourceRank,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM DailyClosing d
            WHERE d.workspace.id = :workspaceId
              AND d.closingDate = :closingDate
            """)
    Optional<DailyClosing> lockByWorkspaceIdAndClosingDate(
            @Param("workspaceId") UUID workspaceId,
            @Param("closingDate") LocalDate closingDate);
}
