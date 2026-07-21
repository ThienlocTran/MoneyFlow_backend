package com.moneyflowbackend.transaction.repository;

import com.moneyflowbackend.transaction.model.TransferDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransferDetailRepository extends JpaRepository<TransferDetail, UUID> {
    interface ActivityTransferContext {
        UUID getTransactionId();
        UUID getSourceWalletId();
        UUID getDestinationWalletId();
    }

    @Query("""
            SELECT td FROM TransferDetail td
            JOIN FETCH td.sourceWallet
            JOIN FETCH td.destinationWallet
            WHERE td.transactionId IN :transactionIds
            """)
    List<TransferDetail> findAllWithWalletsByTransactionIdIn(Collection<UUID> transactionIds);

    @Query("""
            SELECT td.transactionId AS transactionId,
                   source.id AS sourceWalletId,
                   destination.id AS destinationWalletId
            FROM TransferDetail td
            JOIN td.transaction tx
            JOIN td.sourceWallet source
            JOIN td.destinationWallet destination
            WHERE tx.workspace.id = :workspaceId
              AND td.transactionId IN :transactionIds
              AND source.workspace.id = :workspaceId
              AND destination.workspace.id = :workspaceId
            """)
    List<ActivityTransferContext> findActivityContextByWorkspaceIdAndTransactionIdIn(
            @org.springframework.data.repository.query.Param("workspaceId") UUID workspaceId,
            @org.springframework.data.repository.query.Param("transactionIds") Collection<UUID> transactionIds);
}
