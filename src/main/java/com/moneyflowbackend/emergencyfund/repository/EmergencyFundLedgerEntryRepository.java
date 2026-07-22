package com.moneyflowbackend.emergencyfund.repository;

import com.moneyflowbackend.emergencyfund.model.EmergencyFundLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface EmergencyFundLedgerEntryRepository extends JpaRepository<EmergencyFundLedgerEntry, UUID> {
    Page<EmergencyFundLedgerEntry> findAllByWorkspaceIdAndEmergencyFundPlanId(UUID workspaceId, UUID emergencyFundPlanId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(e.amountDelta), 0)
            FROM EmergencyFundLedgerEntry e
            WHERE e.workspace.id = :workspaceId
              AND e.emergencyFundPlan.id = :planId
            """)
    BigDecimal sumReservedAmount(
            @Param("workspaceId") UUID workspaceId,
            @Param("planId") UUID planId);

    @Query("""
            SELECT COALESCE(SUM(e.amountDelta), 0)
            FROM EmergencyFundLedgerEntry e
            WHERE e.workspace.id = :workspaceId
              AND e.emergencyFundPlan.planStatus = com.moneyflowbackend.emergencyfund.model.EmergencyFundPlanStatus.ACTIVE
            """)
    BigDecimal sumActiveWorkspaceReservedAmount(@Param("workspaceId") UUID workspaceId);
}
