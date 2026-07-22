package com.moneyflowbackend.savingsgoal.repository;

import com.moneyflowbackend.savingsgoal.model.SavingsGoal;
import com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {
    Optional<SavingsGoal> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT g FROM SavingsGoal g
            WHERE g.id = :id
              AND g.workspace.id = :workspaceId
            """)
    Optional<SavingsGoal> findByIdAndWorkspaceIdForUpdate(
            @Param("id") UUID id,
            @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT g FROM SavingsGoal g
            WHERE g.workspace.id = :workspaceId
              AND g.status IN :statuses
              AND (:search IS NULL OR LOWER(g.name) LIKE :search OR LOWER(g.description) LIKE :search)
            """)
    Page<SavingsGoal> findGoalPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("statuses") List<SavingsGoalStatus> statuses,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT COUNT(g) > 0 FROM SavingsGoal g
            WHERE g.workspace.id = :workspaceId
              AND g.status <> com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus.ARCHIVED
              AND LOWER(TRIM(g.name)) = LOWER(TRIM(:name))
            """)
    boolean existsOpenNameInWorkspace(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name);

    @Query("""
            SELECT COUNT(g) > 0 FROM SavingsGoal g
            WHERE g.workspace.id = :workspaceId
              AND g.status <> com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus.ARCHIVED
              AND LOWER(TRIM(g.name)) = LOWER(TRIM(:name))
              AND g.id <> :excludeId
            """)
    boolean existsOpenNameInWorkspaceExcluding(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);
}
