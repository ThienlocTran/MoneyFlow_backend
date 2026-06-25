package com.moneyflowbackend.jar.repository;

import com.moneyflowbackend.jar.model.Jar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JarRepository extends JpaRepository<Jar, UUID> {
    List<Jar> findAllByWorkspaceIdAndIsActiveTrue(UUID workspaceId);
    List<Jar> findAllByWorkspaceIdAndIsActiveTrueOrderByDisplayOrderAscNameAsc(UUID workspaceId);
    List<Jar> findAllByWorkspaceIdOrderByDisplayOrderAscNameAsc(UUID workspaceId);
    Optional<Jar> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
    long countByWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceIdAndCodeIgnoreCase(UUID workspaceId, String code);

    @Query("""
            SELECT COUNT(j) > 0 FROM Jar j
            WHERE j.workspace.id = :workspaceId
              AND j.isActive = true
              AND LOWER(j.name) = LOWER(:name)
            """)
    boolean existsActiveName(@Param("workspaceId") UUID workspaceId, @Param("name") String name);

    @Query("""
            SELECT COUNT(j) > 0 FROM Jar j
            WHERE j.workspace.id = :workspaceId
              AND j.isActive = true
              AND LOWER(j.name) = LOWER(:name)
              AND j.id <> :excludeId
            """)
    boolean existsActiveNameExcluding(@Param("workspaceId") UUID workspaceId, @Param("name") String name, @Param("excludeId") UUID excludeId);
}
