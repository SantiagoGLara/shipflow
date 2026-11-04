package com.shipflow.members.dto;

import java.time.Instant;

public record ProjectMemberResponse(Long id, Long userId, String name, String email, String role, Instant joinedAt) {}
