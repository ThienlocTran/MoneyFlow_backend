package com.moneyflowbackend.obligation.repository;

import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringObligationTemplateRepository extends JpaRepository<RecurringObligationTemplate, UUID> {
    List<RecurringObligationTemplate> findAllByWorkspaceIdAndStatusOrderByStartDateAsc(UUID workspaceId, RecurringObligationStatus status);
    List<RecurringObligationTemplate> findAllByWorkspaceIdAndStatusInOrderByStartDateAsc(UUID workspaceId, List<RecurringObligationStatus> statuses);
    List<RecurringObligationTemplate> findAllByWorkspaceIdAndStatusOrderByCreatedAtAsc(UUID workspaceId, RecurringObligationStatus status);
    boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from RecurringObligationTemplate t
            where t.id = :templateId
              and t.workspace.id = :workspaceId
            """)
    Optional<RecurringObligationTemplate> findByIdAndWorkspaceIdForUpdate(
            @Param("templateId") UUID templateId,
            @Param("workspaceId") UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from RecurringObligationTemplate t
            where t.workspace.id = :workspaceId
              and t.status = com.moneyflowbackend.obligation.model.RecurringObligationStatus.ACTIVE
              and t.startDate <= :toDate
              and (t.endDate is null or t.endDate >= :fromDate)
            order by t.startDate asc, t.createdAt asc
            """)
    List<RecurringObligationTemplate> findEligibleActiveForWorkspaceForUpdate(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
