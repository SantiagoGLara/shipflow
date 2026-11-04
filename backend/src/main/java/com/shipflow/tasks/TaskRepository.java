package com.shipflow.tasks;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    Optional<Task> findByIdAndProjectOwnerId(Long id, Long ownerId);
    void deleteByProjectId(Long projectId);
}
