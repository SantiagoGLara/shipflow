package com.shipflow.auth.dto;

public record AuthResponse(
    String token,
    String tokenType,
    UserResponse user
) {}