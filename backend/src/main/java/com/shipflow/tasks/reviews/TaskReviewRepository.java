package com.shipflow.tasks.reviews;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.Optional;
public interface TaskReviewRepository extends JpaRepository<TaskReview,Long>{ List<TaskReview> findAllByTask_IdOrderByCreatedAtDesc(Long taskId); Optional<TaskReview> findFirstByTask_IdAndStatusOrderByCreatedAtDesc(Long taskId,String status); }
