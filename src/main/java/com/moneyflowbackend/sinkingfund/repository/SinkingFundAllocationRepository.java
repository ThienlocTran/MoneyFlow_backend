package com.moneyflowbackend.sinkingfund.repository;

import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SinkingFundAllocationRepository extends JpaRepository<SinkingFundAllocation, UUID> {
    Page<SinkingFundAllocation> findAllByWorkspaceIdAndSinkingFundIdOrderByOccurredAtDescCreatedAtDescIdDesc(UUID workspaceId, UUID sinkingFundId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(a.amountDelta), 0)
            FROM SinkingFundAllocation a
            WHERE a.workspace.id = :workspaceId
              AND a.sinkingFund.id = :sinkingFundId
            """)
    BigDecimal sumReservedAmount(
            @Param("workspaceId") UUID workspaceId,
            @Param("sinkingFundId") UUID sinkingFundId);

    @Query("""
            SELECT a.sinkingFund.id, COALESCE(SUM(a.amountDelta), 0)
            FROM SinkingFundAllocation a
            WHERE a.workspace.id = :workspaceId
              AND a.sinkingFund.id IN :fundIds
            GROUP BY a.sinkingFund.id
            """)
    List<Object[]> sumReservedAmounts(
            @Param("workspaceId") UUID workspaceId,
            @Param("fundIds") List<UUID> fundIds);

    @Query("""
            SELECT COALESCE(SUM(a.amountDelta), 0)
            FROM SinkingFundAllocation a
            WHERE a.workspace.id = :workspaceId
              AND a.sinkingFund.status = com.moneyflowbackend.sinkingfund.model.SinkingFundStatus.ACTIVE
            """)
    BigDecimal sumActiveWorkspaceReservedAmount(@Param("workspaceId") UUID workspaceId);

    Optional<SinkingFundAllocation> findFirstByWorkspaceIdAndSinkingFundIdOrderByOccurredAtDescCreatedAtDescIdDesc(UUID workspaceId, UUID sinkingFundId);
}
