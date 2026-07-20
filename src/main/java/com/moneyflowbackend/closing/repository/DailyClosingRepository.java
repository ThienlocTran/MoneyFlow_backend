package com.moneyflowbackend.closing.repository;

import com.moneyflowbackend.closing.model.DailyClosing;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyClosingRepository extends JpaRepository<DailyClosing, UUID> {
    Optional<DailyClosing> findByWorkspaceIdAndClosingDate(UUID workspaceId, LocalDate closingDate);
    boolean existsByWorkspaceIdAndClosingDate(UUID workspaceId, LocalDate closingDate);

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
