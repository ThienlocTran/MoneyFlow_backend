package com.moneyflowbackend.workspace.repository;

import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.model.WorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {
    Optional<WorkspaceMember> findByWorkspaceIdAndUserIdAndMemberStatus(UUID workspaceId, UUID userId, String status);
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    Optional<WorkspaceMember> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    List<WorkspaceMember> findAllByWorkspaceIdAndMemberStatusOrderByJoinedAtAsc(UUID workspaceId, String status);
    boolean existsByWorkspaceIdAndUserIdAndMemberStatus(UUID workspaceId, UUID userId, String status);
    long countByWorkspaceIdAndRoleAndMemberStatus(UUID workspaceId, WorkspaceRole role, String status);
}
