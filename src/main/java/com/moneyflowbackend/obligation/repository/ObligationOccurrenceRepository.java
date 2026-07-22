package com.moneyflowbackend.obligation.repository;

import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
    interface ActivityObligationContext {
        UUID getOccurrenceId();
        UUID getLinkedTransactionId();
        UUID getTemplateId();
        ObligationDirection getDirection();
        java.math.BigDecimal getActualAmount();
    }

    Optional<ObligationOccurrence> findByTemplateIdAndPeriodKey(UUID templateId, String periodKey);
    Optional<ObligationOccurrence> findTopByTemplateIdOrderByDueDateDesc(UUID templateId);
    List<ObligationOccurrence> findAllByWorkspaceIdAndStatusAndDueDateBetweenOrderByDueDateAsc(
            UUID workspaceId,
            ObligationOccurrenceStatus status,
            LocalDate startDate,
            LocalDate endDate);
    Optional<ObligationOccurrence> findByLinkedTransactionId(UUID linkedTransactionId);
    boolean existsByTemplateId(UUID templateId);

    @Query("""
            SELECT o.id AS occurrenceId,
                   tx.id AS linkedTransactionId,
                   t.id AS templateId,
                   t.direction AS direction,
                   o.actualAmount AS actualAmount
            FROM ObligationOccurrence o
            JOIN o.template t
            JOIN o.linkedTransaction tx
            WHERE o.workspace.id = :workspaceId
              AND t.workspace.id = :workspaceId
              AND tx.workspace.id = :workspaceId
              AND tx.id IN :transactionIds
            """)
    List<ActivityObligationContext> findActivityContextByWorkspaceIdAndLinkedTransactionIdIn(
            @Param("workspaceId") UUID workspaceId,
            @Param("transactionIds") java.util.Collection<UUID> transactionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ObligationOccurrence> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @EntityGraph(attributePaths = {"template", "template.defaultWallet", "template.defaultCategory"})
    @Query(
            value = """
                    select o
                    from ObligationOccurrence o
                    join o.template t
                    where o.workspace.id = :workspaceId
                      and o.status = com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus.PENDING
                      and (:direction is null or t.direction = :direction)
                      and (:templateId is null or t.id = :templateId)
                      and (
                            (:dateFilter = true and (:fromDate is null or o.dueDate >= :fromDate) and (:toDate is null or o.dueDate <= :toDate))
                            or (:dateFilter = false and (o.snoozedUntil > :today or o.dueDate <= :upcomingTo))
                      )
                      and (
                            :groupName is null
                            or (:groupName = 'SNOOZED' and o.snoozedUntil > :today)
                            or (:groupName = 'OVERDUE' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate < :today)
                            or (:groupName = 'DUE_TODAY' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate = :today)
                            or (:groupName = 'UPCOMING' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate > :today and (:dateFilter = true or o.dueDate <= :upcomingTo))
                      )
                    order by
                      case
                        when o.snoozedUntil > :today then 3
                        when o.dueDate < :today then 0
                        when o.dueDate = :today then 1
                        else 2
                      end asc,
                      case when o.snoozedUntil > :today then o.snoozedUntil else o.dueDate end asc,
                      o.dueDate asc,
                      t.name asc,
                      o.id asc
                    """,
            countQuery = """
                    select count(o)
                    from ObligationOccurrence o
                    join o.template t
                    where o.workspace.id = :workspaceId
                      and o.status = com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus.PENDING
                      and (:direction is null or t.direction = :direction)
                      and (:templateId is null or t.id = :templateId)
                      and (
                            (:dateFilter = true and (:fromDate is null or o.dueDate >= :fromDate) and (:toDate is null or o.dueDate <= :toDate))
                            or (:dateFilter = false and (o.snoozedUntil > :today or o.dueDate <= :upcomingTo))
                      )
                      and (
                            :groupName is null
                            or (:groupName = 'SNOOZED' and o.snoozedUntil > :today)
                            or (:groupName = 'OVERDUE' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate < :today)
                            or (:groupName = 'DUE_TODAY' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate = :today)
                            or (:groupName = 'UPCOMING' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate > :today and (:dateFilter = true or o.dueDate <= :upcomingTo))
                      )
                    """)
    Page<ObligationOccurrence> findInboxPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("direction") ObligationDirection direction,
            @Param("templateId") UUID templateId,
            @Param("groupName") String groupName,
            @Param("dateFilter") boolean dateFilter,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("today") LocalDate today,
            @Param("upcomingTo") LocalDate upcomingTo,
            Pageable pageable);

    @Query("""
            select count(o)
            from ObligationOccurrence o
            join o.template t
            where o.workspace.id = :workspaceId
              and o.status = com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus.PENDING
              and (:direction is null or t.direction = :direction)
              and (:templateId is null or t.id = :templateId)
              and (
                    (:dateFilter = true and (:fromDate is null or o.dueDate >= :fromDate) and (:toDate is null or o.dueDate <= :toDate))
                    or (:dateFilter = false and (o.snoozedUntil > :today or o.dueDate <= :upcomingTo))
              )
              and (
                    (:groupName = 'SNOOZED' and o.snoozedUntil > :today)
                    or (:groupName = 'OVERDUE' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate < :today)
                    or (:groupName = 'DUE_TODAY' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate = :today)
                    or (:groupName = 'UPCOMING' and (o.snoozedUntil is null or o.snoozedUntil <= :today) and o.dueDate > :today and (:dateFilter = true or o.dueDate <= :upcomingTo))
              )
            """)
    long countInboxGroup(
            @Param("workspaceId") UUID workspaceId,
            @Param("direction") ObligationDirection direction,
            @Param("templateId") UUID templateId,
            @Param("groupName") String groupName,
            @Param("dateFilter") boolean dateFilter,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("today") LocalDate today,
            @Param("upcomingTo") LocalDate upcomingTo);

    @EntityGraph(attributePaths = {"template", "template.defaultWallet", "template.defaultCategory"})
    @Query(
            value = """
                    select o
                    from ObligationOccurrence o
                    join o.template t
                    where o.workspace.id = :workspaceId
                      and t.id = :templateId
                      and (:status is null or o.status = :status)
                      and (:fromDate is null or o.dueDate >= :fromDate)
                      and (:toDate is null or o.dueDate <= :toDate)
                    order by o.dueDate desc, o.id asc
                    """,
            countQuery = """
                    select count(o)
                    from ObligationOccurrence o
                    join o.template t
                    where o.workspace.id = :workspaceId
                      and t.id = :templateId
                      and (:status is null or o.status = :status)
                      and (:fromDate is null or o.dueDate >= :fromDate)
                      and (:toDate is null or o.dueDate <= :toDate)
                    """)
    Page<ObligationOccurrence> findHistoryPage(
            @Param("workspaceId") UUID workspaceId,
            @Param("templateId") UUID templateId,
            @Param("status") ObligationOccurrenceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("""
            select o.template.id, count(o)
            from ObligationOccurrence o
            where o.template.id in :templateIds
            group by o.template.id
            """)
    List<Object[]> countByTemplateIds(@Param("templateIds") List<UUID> templateIds);

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

    @EntityGraph(attributePaths = {"template"})
    @Query("""
            select o
            from ObligationOccurrence o
            join o.template t
            where o.workspace.id = :workspaceId
              and t.workspace.id = :workspaceId
              and o.status = com.moneyflowbackend.obligation.model.ObligationOccurrenceStatus.PENDING
              and t.direction = com.moneyflowbackend.obligation.model.ObligationDirection.PAYABLE
              and o.linkedTransaction is null
              and o.dueDate between :fromDate and :toDate
            order by o.dueDate asc, t.name asc, o.id asc
            """)
    List<ObligationOccurrence> findPendingPayablePlanningOccurrences(
            @Param("workspaceId") UUID workspaceId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
