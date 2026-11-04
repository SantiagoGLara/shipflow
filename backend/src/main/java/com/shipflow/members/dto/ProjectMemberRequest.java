package com.shipflow.members.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProjectMemberRequest(
    @NotBlank @Email String email,
    @NotBlank @Pattern(regexp = "LEAD|DEVELOPER|REVIEWER|DEPLOYER", message = "Rol inválido") String role
) {}
