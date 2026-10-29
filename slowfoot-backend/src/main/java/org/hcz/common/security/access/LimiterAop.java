package org.hcz.common.security.access;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hcz.common.constant.RedisKeys;
import org.hcz.common.exception.LimiterException;
import org.hcz.common.security.SecurityAuditLogger;
import org.hcz.common.util.RedisCacheUtil;
import org.hcz.common.util.UserHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Aspect
@Component
public class LimiterAop {

    private final RedisCacheUtil redisCacheUtil;
    private final HttpServletRequest request;


    private final SecurityAuditLogger securityAuditLogger;

    public LimiterAop(RedisCacheUtil redisCacheUtil, HttpServletRequest request,
                      SecurityAuditLogger securityAuditLogger) {
        this.redisCacheUtil = redisCacheUtil;
        this.request = request;
        this.securityAuditLogger = securityAuditLogger;
    }

    @Around("@annotation(limit)")
    public Object restriction(ProceedingJoinPoint joinPoint, Limit limit) throws Throwable {
        validate(limit);

        String bucket = StringUtils.hasText(limit.key())
                ? limit.key()
                : RedisKeys.WORK_LIMIT + ":" + joinPoint.getSignature().getDeclaringTypeName()
                        + ":" + joinPoint.getSignature().getName();
        String subject = UserHolder.get() == null ? request.getRemoteAddr() : UserHolder.get().toString();
        String key = bucket + ":" + subject;

        // Lua 脚本原子完成 INCR + EXPIRE，不拆分两步；Redis 不可用时抛异常 fail-close，
        // 不静默放开受保护接口。
        Long count;
        try {
            count = redisCacheUtil.incrWithWindow(key, limit.time());
        } catch (DataAccessException e) {
            throw new IllegalStateException("Unable to update rate limit counter", e);
        }
        if (count == null) {
            throw new IllegalStateException("Unable to update rate limit counter");
        }
        if (count > limit.limit()) {
            securityAuditLogger.rateLimitExceeded(UserHolder.get(), bucket);
            throw new LimiterException(limit.msg());
        }
        return joinPoint.proceed();
    }

    private void validate(Limit limit) {
        if (limit.limit() <= 0 || limit.time() <= 0) {
            throw new IllegalArgumentException("Limit and time must be greater than zero");
        }
        if (request == null && UserHolder.get() == null) {
            throw new IllegalStateException("HTTP request is required for rate limiting");
        }
    }
}
