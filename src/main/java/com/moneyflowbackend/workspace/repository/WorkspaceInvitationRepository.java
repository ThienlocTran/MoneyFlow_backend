package com.moneyflowbackend.workspace.repository;

import com.moneyflowbackend.workspace.model.InvitationStatus;
import com.moneyflowbackend.workspace.model.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {
    List<WorkspaceInvitation> findAllByInvitedUserIdAndInvitationStatusOrderByCreatedAtDesc(UUID userId, InvitationStatus status);
    List<WorkspaceInvitation> findAllByWorkspaceIdAndInvitationStatusOrderByCreatedAtDesc(UUID workspaceId, InvitationStatus status);
    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    boolean existsByWorkspaceIdAndInvitedUserIdAndInvitationStatus(UUID workspaceId, UUID invitedUserId, InvitationStatus status);
}
