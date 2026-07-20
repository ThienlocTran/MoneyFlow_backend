package com.moneyflowbackend.wallet.repository;

import com.moneyflowbackend.wallet.model.WalletBalanceSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletBalanceSnapshotRepository extends JpaRepository<WalletBalanceSnapshot, UUID> {
    Optional<WalletBalanceSnapshot> findByDailyClosingIdAndWalletId(UUID dailyClosingId, UUID walletId);

    List<WalletBalanceSnapshot> findAllByWorkspaceIdAndWalletIdAndSnapshotDateBetweenOrderBySnapshotDateDescRecordedAtDescCreatedAtDesc(
            UUID workspaceId,
            UUID walletId,
            LocalDate fromDate,
            LocalDate toDate);

    Page<WalletBalanceSnapshot> findAllByWorkspaceIdOrderBySnapshotDateDescRecordedAtDescCreatedAtDesc(UUID workspaceId, Pageable pageable);
    Optional<WalletBalanceSnapshot> findByIdAndWorkspaceId(UUID snapshotId, UUID workspaceId);
}
