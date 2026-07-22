package com.moneyflowbackend.emergencyfund.repository;

import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EmergencyFundPlanRepository extends JpaRepository<EmergencyFundPlan, UUID> {
    Optional<EmergencyFundPlan> findByWorkspaceId(UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM EmergencyFundPlan p
            WHERE p.workspace.id = :workspaceId
            """)
    Optional<EmergencyFundPlan> findByWorkspaceIdForUpdate(@Param("workspaceId") UUID workspaceId);
}
