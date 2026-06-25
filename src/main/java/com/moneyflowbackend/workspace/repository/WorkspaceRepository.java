package com.moneyflowbackend.workspace.repository;

import com.moneyflowbackend.workspace.model.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    @Query("SELECT wm.workspace FROM WorkspaceMember wm WHERE wm.user.id = :userId AND wm.workspace.deletedAt IS NULL AND wm.memberStatus = 'ACTIVE' ORDER BY wm.joinedAt DESC")
    List<Workspace> findAllByUserId(@Param("userId") UUID userId);
}
