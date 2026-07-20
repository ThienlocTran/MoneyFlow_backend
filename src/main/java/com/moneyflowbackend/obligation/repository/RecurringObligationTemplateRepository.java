package com.moneyflowbackend.obligation.repository;

import com.moneyflowbackend.obligation.model.RecurringObligationStatus;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringObligationTemplateRepository extends JpaRepository<RecurringObligationTemplate, UUID> {
    List<RecurringObligationTemplate> findAllByWorkspaceIdAndStatusOrderByStartDateAsc(UUID workspaceId, RecurringObligationStatus status);
    List<RecurringObligationTemplate> findAllByWorkspaceIdAndStatusInOrderByStartDateAsc(UUID workspaceId, List<RecurringObligationStatus> statuses);
    List<RecurringObligationTemplate> findAllByWorkspaceIdAndStatusOrderByCreatedAtAsc(UUID workspaceId, RecurringObligationStatus status);
    boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
