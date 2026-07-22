package com.moneyflowbackend.studentloan.repository;

import com.moneyflowbackend.studentloan.model.StudentLoan;
import com.moneyflowbackend.studentloan.model.StudentLoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StudentLoanRepository extends JpaRepository<StudentLoan, UUID> {
    @Query("""
            SELECT l FROM StudentLoan l
            WHERE l.id = :id
              AND l.workspace.id = :workspaceId
            """)
    Optional<StudentLoan> findByIdAndWorkspaceId(
            @Param("id") UUID id,
            @Param("workspaceId") UUID workspaceId);

    @Query("""
            SELECT l FROM StudentLoan l
            WHERE l.workspace.id = :workspaceId
              AND l.status = :status
            """)
    Page<StudentLoan> findAllByWorkspaceIdAndStatus(
            @Param("workspaceId") UUID workspaceId,
            @Param("status") StudentLoanStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT l FROM StudentLoan l
            WHERE l.id = :id
              AND l.workspace.id = :workspaceId
            """)
    Optional<StudentLoan> findByIdAndWorkspaceIdForUpdate(
            @Param("id") UUID id,
            @Param("workspaceId") UUID workspaceId);
}
