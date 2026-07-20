package com.moneyflowbackend.obligation.repository;

import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Query("""
            select o.periodKey
            from ObligationOccurrence o
            where o.template.id = :templateId
              and o.dueDate between :fromDate and :toDate
            """)
    Set<String> findPeriodKeysByTemplateIdAndDueDateBetween(
            @Param("templateId") UUID templateId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
