package com.farmflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    public record LoginRequest(
        @NotBlank(message = "E-mail Ã© obrigatÃ³rio")
        @Email(message = "E-mail invÃ¡lido")
        String email,

        @NotBlank(message = "Senha Ã© obrigatÃ³ria")
        String password
    ) {}

    public record RegisterRequest(
        @NotBlank(message = "Nome Ã© obrigatÃ³rio")
        String name,

        @NotBlank(message = "E-mail Ã© obrigatÃ³rio")
        @Email(message = "E-mail invÃ¡lido")
        String email,

        @NotBlank(message = "Senha Ã© obrigatÃ³ria")
        String password,

        String role
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