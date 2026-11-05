package com.shipflow.tasks.reviews.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public record ReviewDecisionRequest(@NotBlank String status,@Size(max=5000) String comment) {}
