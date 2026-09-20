package org.hcz.security.access;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hcz.exception.AuthorityException;
import org.hcz.security.SecurityAuditLogger;
import org.hcz.util.JwtUtils;
import org.springframework.stereotype.Component;

@Aspect
@Component
public final class AuthAspect {

    private final HttpServletRequest request;
    private final SecurityAuditLogger securityAuditLogger;

    public AuthAspect(HttpServletRequest request, SecurityAuditLogger securityAuditLogger) {
        this.request = request;
        this.securityAuditLogger = securityAuditLogger;
    }

    @Around("@annotation(auth)")
    public Object check(ProceedingJoinPoint joinPoint, Auth auth) throws Throwable {
        if (!AuthStore.enabled()) {
            return joinPoint.proceed();
        }
        if (!JwtUtils.checkAccessToken(request)) {
            securityAuditLogger.authorizationDenied(null, "invalid_access_token", null);
            throw new AuthorityException("请先登录");
        }

        Long userId = JwtUtils.getUserId(request);
        for (String permission : auth.value()) {
            if (!AuthStore.has(userId, permission)) {
                securityAuditLogger.authorizationDenied(userId, "permission_missing", permission);
                throw new AuthorityException("权限不足");
            }
        }
        return joinPoint.proceed();
    }
}
