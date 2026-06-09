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

    /**
     * 要求当前登录用户具备管理员权限。
     *
     * <p>用于保护 RAG 运维和后台任务查询接口；不要依赖前端隐藏入口作为权限边界。</p>
     */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new BusinessException("需要管理员权限");
        }
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
