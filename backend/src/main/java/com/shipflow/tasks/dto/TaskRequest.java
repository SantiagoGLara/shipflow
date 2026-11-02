package com.shipflow.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TaskRequest(

    @NotBlank(message = "El título de la tarea es obligatorio")
    @Size(max = 180, message = "El título no puede superar 180 caracteres")
    String title,

    @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres")
    String description,

    @Pattern(
        regexp = "PENDING|IN_PROGRESS|COMPLETED",
        message = "El estado debe ser PENDING, IN_PROGRESS o COMPLETED"
    )
    String status
) {
}
