package com.shipflow.tasks.dto;

import java.time.Instant;

public record TaskResponse(
    Long id,
    Long projectId,
    String title,
    String description,
    String status,
    Instant createdAt
) {
}
