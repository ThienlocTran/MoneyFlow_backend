package com.moneyflowbackend.workspace.repository;

import com.moneyflowbackend.workspace.model.WorkspacePerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspacePersonRepository extends JpaRepository<WorkspacePerson, UUID> {
    Optional<WorkspacePerson> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
