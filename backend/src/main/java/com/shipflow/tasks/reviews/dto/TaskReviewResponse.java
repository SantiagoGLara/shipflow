package com.shipflow.tasks.reviews.dto;
import java.time.Instant;
public record TaskReviewResponse(Long id,Long taskId,String taskTitle,Long requestedById,String requestedByName,Long reviewedById,String reviewedByName,String status,String comment,Instant createdAt,Instant reviewedAt) {}
