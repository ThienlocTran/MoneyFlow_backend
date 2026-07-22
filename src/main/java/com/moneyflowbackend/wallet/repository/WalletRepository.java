package com.moneyflowbackend.wallet.repository;

import com.moneyflowbackend.wallet.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    List<Wallet> findAllByWorkspaceIdAndIsActiveTrue(UUID workspaceId);
    List<Wallet> findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(UUID workspaceId);
    List<Wallet> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);
    List<Wallet> findAllByWorkspaceIdAndIdInAndIsActiveTrue(UUID workspaceId, List<UUID> walletIds);
    Optional<Wallet> findByIdAndWorkspaceId(UUID walletId, UUID workspaceId);
    Optional<Wallet> findByWorkspaceIdAndIsDefaultTrueAndIsActiveTrue(UUID workspaceId);
    long countByWorkspaceId(UUID workspaceId);
    long countByWorkspaceIdAndIsActiveTrue(UUID workspaceId);

    @Query("""
            SELECT w FROM Wallet w
            WHERE w.workspace.id = :workspaceId
              AND w.isActive = true
              AND (w.openingDate IS NULL OR w.openingDate <= :closingDate)
            ORDER BY w.createdAt ASC
            """)
    List<Wallet> findActiveOpenOnDate(
            @Param("workspaceId") UUID workspaceId,
            @Param("closingDate") java.time.LocalDate closingDate);

    @Query("""
            SELECT COUNT(w) > 0 FROM Wallet w
            WHERE w.workspace.id = :workspaceId
              AND LOWER(w.name) = LOWER(:name)
            """)
    boolean existsName(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name);

    @Query("""
            SELECT COUNT(w) > 0 FROM Wallet w
            WHERE w.workspace.id = :workspaceId
              AND LOWER(w.name) = LOWER(:name)
              AND w.id <> :excludeId
            """)
    boolean existsNameExcluding(
            @Param("workspaceId") UUID workspaceId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Wallet w SET w.isDefault = false WHERE w.workspace.id = :workspaceId AND w.isDefault = true")
    int clearDefault(@Param("workspaceId") UUID workspaceId);
}
