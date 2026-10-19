package com.farmflow.service;

import com.farmflow.config.JwtTokenProvider;
import com.farmflow.domain.User;
import com.farmflow.dto.AuthDtos.*;
import com.farmflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        long expiresAt = System.currentTimeMillis() + tokenProvider.getExpirationMs();

        return new AuthResponse(token, toUserResponse(user), expiresAt);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }

        String role = (request.role() != null && !request.role().isBlank()) ? request.role() : "owner";

        User user = User.builder()
            .name(request.name())
            .email(request.email())
            .password(passwordEncoder.encode(request.password()))
            .role(role)
            .build();

        user = userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        long expiresAt = System.currentTimeMillis() + tokenProvider.getExpirationMs();

        return new AuthResponse(token, toUserResponse(user), expiresAt);
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            user.getFarmId(),
            user.getAvatarUrl()
        );
    }
}
