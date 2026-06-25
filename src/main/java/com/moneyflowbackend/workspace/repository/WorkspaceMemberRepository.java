package com.moneyflowbackend.workspace.repository;

import com.moneyflowbackend.workspace.model.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {
    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndMemberStatus(UUID workspaceId, UUID userId, String status);
    boolean existsByWorkspaceIdAndUserIdAndMemberStatus(UUID workspaceId, UUID userId, String status);
}