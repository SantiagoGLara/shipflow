package com.shipflow.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectRequest(

    @NotBlank(message = "El nombre del proyecto es obligatorio")
    @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
    String name,

    @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres")
    String description,

    @Pattern(
        regexp = "ACTIVE|ARCHIVED",
        message = "El estado debe ser ACTIVE o ARCHIVED"
    )
    String status
) {
}
