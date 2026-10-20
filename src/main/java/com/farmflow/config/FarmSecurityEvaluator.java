package com.farmflow.config;

import com.farmflow.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("farmSecurity")
public class FarmSecurityEvaluator {

    public boolean hasFarmAccess(String farmId) {
        if (farmId == null) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                return true;
            }
            return farmId.equals(user.getFarmId());
        }
        if (principal instanceof UserPrincipal userPrincipal) {
            if ("ADMIN".equalsIgnoreCase(userPrincipal.getRole())) {
                return true;
            }
            return farmId.equals(userPrincipal.getFarmId());
        }
        return false;
    }

    public boolean isCurrentUser(String userId) {
        if (userId == null) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof User user) {
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                return true;
            }
            return userId.equals(user.getId());
        }
        if (principal instanceof UserPrincipal userPrincipal) {
            if ("ADMIN".equalsIgnoreCase(userPrincipal.getRole())) {
                return true;
            }
            return userId.equals(userPrincipal.getId());
        }
        return false;
    }
}
