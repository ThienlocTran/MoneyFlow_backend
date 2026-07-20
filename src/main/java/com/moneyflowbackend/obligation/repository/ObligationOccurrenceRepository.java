package com.moneyflowbackend.obligation.repository;

import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ObligationOccurrenceRepository extends JpaRepository<ObligationOccurrence, UUID> {
    Optional<ObligationOccurrence> findByTemplateIdAndPeriodKey(UUID templateId, String periodKey);
    Optional<ObligationOccurrence> findTopByTemplateIdOrderByDueDateDesc(UUID templateId);
    List<ObligationOccurrence> findAllByWorkspaceIdAndStatusAndDueDateBetweenOrderByDueDateAsc(
            UUID workspaceId,
            ObligationOccurrenceStatus status,
            LocalDate startDate,
            LocalDate endDate);
    Optional<ObligationOccurrence> findByLinkedTransactionId(UUID linkedTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ObligationOccurrence> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
