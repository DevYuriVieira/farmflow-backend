package com.farmflow.security;

import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.config.UserPrincipal;
import com.farmflow.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmSecurityEvaluatorTest {

    private FarmSecurityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new FarmSecurityEvaluator();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDenyFarmAccessWhenFarmIdIsNull() {
        assertFalse(evaluator.hasFarmAccess(null));
    }

    @Test
    void shouldDenyFarmAccessWhenUnauthenticated() {
        assertFalse(evaluator.hasFarmAccess("farm-1"));
    }

    @Test
    void shouldDenyFarmAccessWhenPrincipalIsUnrecognizedType() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null)
        );
        assertFalse(evaluator.hasFarmAccess("farm-1"));
    }

    @Test
    void shouldAllowFarmAccessWhenUserIsAdmin() {
        User adminUser = User.builder()
                .name("Admin User")
                .email("admin@farmflow.com")
                .role("ADMIN")
                .farmId("farm-admin")
                .build();
        adminUser.setId("admin-1");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null)
        );

        assertTrue(evaluator.hasFarmAccess("farm-any-id"));
    }

    @Test
    void shouldAllowFarmAccessWhenUserBelongsToTargetFarm() {
        User regularUser = User.builder()
                .name("Farmer Joe")
                .email("farmer@farmflow.com")
                .role("FARM_MANAGER")
                .farmId("farm-target-123")
                .build();
        regularUser.setId("user-1");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(regularUser, null)
        );

        assertTrue(evaluator.hasFarmAccess("farm-target-123"));
    }

    @Test
    void shouldDenyCrossTenantFarmAccessWhenUserBelongsToDifferentFarm() {
        User regularUser = User.builder()
                .name("Farmer Joe")
                .email("farmer@farmflow.com")
                .role("FARM_MANAGER")
                .farmId("farm-source-111")
                .build();
        regularUser.setId("user-1");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(regularUser, null)
        );

        assertFalse(evaluator.hasFarmAccess("farm-target-222"));
    }

    @Test
    void shouldAllowFarmAccessWhenUserPrincipalIsAdmin() {
        UserPrincipal adminPrincipal = UserPrincipal.builder()
                .id("admin-2")
                .email("admin@farmflow.com")
                .role("ADMIN")
                .farmId("farm-1")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, null)
        );

        assertTrue(evaluator.hasFarmAccess("farm-other-999"));
    }

    @Test
    void shouldAllowFarmAccessWhenUserPrincipalBelongsToTargetFarm() {
        UserPrincipal agronomist = UserPrincipal.builder()
                .id("user-3")
                .email("agro@farmflow.com")
                .role("AGRONOMIST")
                .farmId("farm-agri-444")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(agronomist, null)
        );

        assertTrue(evaluator.hasFarmAccess("farm-agri-444"));
        assertFalse(evaluator.hasFarmAccess("farm-different-555"));
    }

    @Test
    void shouldDenyIsCurrentUserWhenUserIdIsNull() {
        assertFalse(evaluator.isCurrentUser(null));
    }

    @Test
    void shouldDenyIsCurrentUserWhenUnauthenticated() {
        assertFalse(evaluator.isCurrentUser("user-1"));
    }

    @Test
    void shouldAllowIsCurrentUserWhenUserIsAdmin() {
        User admin = User.builder()
                .name("Admin")
                .role("ADMIN")
                .build();
        admin.setId("admin-1");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null)
        );

        assertTrue(evaluator.isCurrentUser("any-user-id"));
    }

    @Test
    void shouldEvaluateIsCurrentUserMatchingForUserEntity() {
        User user = User.builder()
                .name("Operator")
                .role("OPERATOR")
                .build();
        user.setId("user-self-777");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );

        assertTrue(evaluator.isCurrentUser("user-self-777"));
        assertFalse(evaluator.isCurrentUser("user-other-888"));
    }

    @Test
    void shouldEvaluateIsCurrentUserMatchingForUserPrincipal() {
        UserPrincipal principal = UserPrincipal.builder()
                .id("principal-999")
                .role("OPERATOR")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );

        assertTrue(evaluator.isCurrentUser("principal-999"));
        assertFalse(evaluator.isCurrentUser("principal-000"));
    }
}
