package org.luckyjourney;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.hcz.constant.RedisKeys;
import org.hcz.exception.LimiterException;
import org.hcz.limit.Limit;
import org.hcz.limit.LimiterAop;
import org.hcz.util.RedisCacheUtil;
import org.hcz.util.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LimitFrameworkTests {

    @AfterEach
    void clearUser() {
        UserHolder.clear();
    }

    @Test
    void incrementsTheAtomicCounterAndSetsWindowOnFirstRequest() throws Throwable {
        RedisCacheUtil redis = mock(RedisCacheUtil.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        Limit limit = limit(2, 60, "");
        String key = RedisKeys.WORK_LIMIT + ":ExampleController:publish:42";

        UserHolder.set(42L);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn("ExampleController");
        when(signature.getName()).thenReturn("publish");
        when(redis.incr(key, 1)).thenReturn(1L);
        when(redis.expire(key, 60)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = new LimiterAop(redis, request).restriction(joinPoint, limit);

        assertEquals("ok", result);
        verify(redis).expire(key, 60);
        verify(joinPoint).proceed();
    }

    @Test
    void rejectsAfterTheConfiguredLimitWithoutCallingTheTarget() throws Throwable {
        RedisCacheUtil redis = mock(RedisCacheUtil.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Limit limit = limit(2, 60, "custom");

        UserHolder.set(42L);
        when(redis.incr("custom:42", 1)).thenReturn(3L);

        LimiterException exception = assertThrows(LimiterException.class,
                () -> new LimiterAop(redis, request).restriction(joinPoint, limit));
        assertEquals("too many", exception.getMessage());
        verify(joinPoint, never()).proceed();
    }

    private Limit limit(int count, long seconds, String key) {
        Limit limit = mock(Limit.class);
        when(limit.limit()).thenReturn(count);
        when(limit.time()).thenReturn(seconds);
        when(limit.key()).thenReturn(key);
        when(limit.msg()).thenReturn("too many");
        return limit;
    }
}
