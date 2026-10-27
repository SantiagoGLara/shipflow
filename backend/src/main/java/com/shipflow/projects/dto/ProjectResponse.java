
package com.shipflow.projects.dto;

import java.time.Instant;

public record ProjectResponse(
    Long id,
    String name,
    String description,
    String status,
    Instant createdAt
) {
}
