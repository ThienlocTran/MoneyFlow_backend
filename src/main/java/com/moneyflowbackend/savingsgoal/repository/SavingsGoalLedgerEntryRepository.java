package com.moneyflowbackend.savingsgoal.repository;

import com.moneyflowbackend.savingsgoal.model.SavingsGoalLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SavingsGoalLedgerEntryRepository extends JpaRepository<SavingsGoalLedgerEntry, UUID> {
    Page<SavingsGoalLedgerEntry> findAllByWorkspaceIdAndSavingsGoalId(UUID workspaceId, UUID savingsGoalId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(e.amountDelta), 0)
            FROM SavingsGoalLedgerEntry e
            WHERE e.workspace.id = :workspaceId
              AND e.savingsGoal.id = :goalId
            """)
    BigDecimal sumReservedAmount(
            @Param("workspaceId") UUID workspaceId,
            @Param("goalId") UUID goalId);

    @Query("""
            SELECT e.savingsGoal.id, COALESCE(SUM(e.amountDelta), 0)
            FROM SavingsGoalLedgerEntry e
            WHERE e.workspace.id = :workspaceId
              AND e.savingsGoal.id IN :goalIds
            GROUP BY e.savingsGoal.id
            """)
    List<Object[]> sumReservedAmountByGoalIds(
            @Param("workspaceId") UUID workspaceId,
            @Param("goalIds") List<UUID> goalIds);

    @Query("""
            SELECT COALESCE(SUM(e.amountDelta), 0)
            FROM SavingsGoalLedgerEntry e
            WHERE e.workspace.id = :workspaceId
              AND e.savingsGoal.status = com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus.ACTIVE
            """)
    BigDecimal sumActiveWorkspaceReservedAmount(@Param("workspaceId") UUID workspaceId);
}
