package org.hcz.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hcz.common.exception.AuthorityException;
import org.hcz.common.security.SecurityAuditLogger;
import org.hcz.common.util.JwtUtils;
import org.springframework.stereotype.Component;

@Aspect
@Component
public final class AuthAspect {

    private final HttpServletRequest request;
    private final SecurityAuditLogger securityAuditLogger;
    private final AuthStore authStore;

    public AuthAspect(HttpServletRequest request, SecurityAuditLogger securityAuditLogger, AuthStore authStore) {
        this.request = request;
        this.securityAuditLogger = securityAuditLogger;
        this.authStore = authStore;
    }

    @Around("@annotation(auth)")
    public Object check(ProceedingJoinPoint joinPoint, Auth auth) throws Throwable {
        if (!authStore.enabled()) {
            return joinPoint.proceed();
        }
        if (!JwtUtils.checkAccessToken(request)) {
            securityAuditLogger.authorizationDenied(null, "invalid_access_token", null);
            throw new AuthorityException("请先登录");
        }

        Long userId = JwtUtils.getUserId(request);
        for (String permission : auth.value()) {
            if (!authStore.has(userId, permission)) {
                securityAuditLogger.authorizationDenied(userId, "permission_missing", permission);
                throw new AuthorityException("权限不足");
            }
        }
        return joinPoint.proceed();
    }
}
