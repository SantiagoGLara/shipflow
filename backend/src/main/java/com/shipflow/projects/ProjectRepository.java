package com.shipflow.projects;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);
    @Query(value = "select distinct p.* from projects p left join project_members m on m.project_id=p.id where p.owner_id=:userId or m.user_id=:userId order by p.created_at desc", nativeQuery = true)
    List<Project> findAccessibleByUser(@Param("userId") Long userId);
    @Query(value = "select distinct p.* from projects p left join project_members m on m.project_id=p.id where p.id=:projectId and (p.owner_id=:userId or m.user_id=:userId)", nativeQuery = true)
    Optional<Project> findAccessibleByIdAndUser(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
