package com.jinmo.essayevaluator.service;

import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.security.UserAccountPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public Long requireUserId() {
        return requirePrincipal().id();
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserAccountPrincipal principal)) {
            return false;
        }
        return "ADMIN".equals(principal.role());
    }

    public UserAccountPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof UserAccountPrincipal principal)) {
            throw new BusinessException("请先登录");
        }
        return principal;
    }
}
