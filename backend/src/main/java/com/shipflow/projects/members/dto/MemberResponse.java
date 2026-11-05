package com.shipflow.projects.members.dto;

import java.time.Instant;

public record MemberResponse(
    Long id,
    String name,
    String email,
    String role,
    Instant joinedAt
) {
}