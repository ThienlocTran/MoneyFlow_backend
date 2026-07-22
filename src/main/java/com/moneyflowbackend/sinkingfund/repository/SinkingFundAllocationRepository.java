package com.moneyflowbackend.sinkingfund.repository;

import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface SinkingFundAllocationRepository extends JpaRepository<SinkingFundAllocation, UUID> {
    Page<SinkingFundAllocation> findAllByWorkspaceIdAndSinkingFundId(UUID workspaceId, UUID sinkingFundId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(a.amountDelta), 0)
            FROM SinkingFundAllocation a
            WHERE a.workspace.id = :workspaceId
              AND a.sinkingFund.id = :sinkingFundId
            """)
    BigDecimal sumReservedAmount(
            @Param("workspaceId") UUID workspaceId,
            @Param("sinkingFundId") UUID sinkingFundId);

    Optional<SinkingFundAllocation> findFirstByWorkspaceIdAndSinkingFundIdOrderByOccurredAtDescCreatedAtDescIdDesc(UUID workspaceId, UUID sinkingFundId);
}
