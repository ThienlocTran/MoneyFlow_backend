package com.moneyflowbackend.planning.repository;

import com.moneyflowbackend.planning.model.PlanningPreference;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlanningPreferenceRepository extends JpaRepository<PlanningPreference, UUID> {
    Optional<PlanningPreference> findByWorkspaceId(UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PlanningPreference p
            where p.workspace.id = :workspaceId
            """)
    Optional<PlanningPreference> findByWorkspaceIdForUpdate(@Param("workspaceId") UUID workspaceId);
}
