package com.moneyflowbackend.transaction.repository;

import com.moneyflowbackend.transaction.model.TransferDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransferDetailRepository extends JpaRepository<TransferDetail, UUID> {
    @Query("""
            SELECT td FROM TransferDetail td
            JOIN FETCH td.sourceWallet
            JOIN FETCH td.destinationWallet
            WHERE td.transactionId IN :transactionIds
            """)
    List<TransferDetail> findAllWithWalletsByTransactionIdIn(Collection<UUID> transactionIds);
}
