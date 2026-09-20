package org.hcz.security.access;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hcz.constant.RedisKeys;
import org.hcz.exception.LimiterException;
import org.hcz.security.SecurityAuditLogger;
import org.hcz.util.RedisCacheUtil;
import org.hcz.util.UserHolder;
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

        Long count = redisCacheUtil.incr(key, 1);
        if (count == null) {
            throw new IllegalStateException("Unable to update rate limit counter");
        }
        if (count == 1 && !redisCacheUtil.expire(key, limit.time())) {
            redisCacheUtil.del(key);
            throw new IllegalStateException("Unable to initialize rate limit window");
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
