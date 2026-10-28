package com.farmflow.config;

import com.farmflow.entity.User;
import com.farmflow.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = resolveToken(request);

        try {
            if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
                String userId = tokenProvider.getUserIdFromToken(token);
                Optional<User> userOptional = userRepository.findById(userId);

                if (userOptional.isPresent()) {
                    User user = userOptional.get();
                    var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase());
                    var auth = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    String targetFarmId = user.getFarmId();
                    if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                        String headerFarmId = request.getHeader("X-Farm-Id");
                        if (StringUtils.hasText(headerFarmId)) {
                            targetFarmId = headerFarmId.trim();
                        }
                    }
                    TenantContext.setTenantId(targetFarmId);
                } else {
                    SecurityContextHolder.clearContext();
                    TenantContext.clear();
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
