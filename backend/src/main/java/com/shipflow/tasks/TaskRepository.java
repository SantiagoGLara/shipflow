package com.shipflow.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    Optional<Task> findByIdAndProjectOwnerId(Long id, Long ownerId);
    void deleteByProjectId(Long projectId);
    @Query(value = "select distinct t.* from tasks t join projects p on p.id=t.project_id left join project_members m on m.project_id=p.id where t.id=:taskId and (p.owner_id=:userId or m.user_id=:userId)", nativeQuery = true)
    Optional<Task> findAccessible(@Param("taskId") Long taskId, @Param("userId") Long userId);
}
