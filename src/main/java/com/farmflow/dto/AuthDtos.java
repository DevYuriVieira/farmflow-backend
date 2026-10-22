package com.farmflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    public record LoginRequest(
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        String password
    ) {}

    public record RegisterRequest(
        @NotBlank(message = "Nome é obrigatório")
        String name,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        String password
    ) {}

    public record AuthResponse(
        String token,
        UserResponse user,
        long expiresAt
    ) {}

    public record UserResponse(
        String id,
        String name,
        String email,
        String role,
        String farmId,
        String avatarUrl
    ) {}

    public record UpdateProfileRequest(
        String name,
        String avatarUrl
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "Senha atual é obrigatória")
        String currentPassword,

        @NotBlank(message = "Nova senha é obrigatória")
        String newPassword
    ) {}
}