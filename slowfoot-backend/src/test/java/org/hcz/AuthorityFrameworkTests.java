package org.hcz;

import org.hcz.admin.security.AuthStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

class AuthorityFrameworkTests {

    private static final long USER_ID = 9001L;

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    private AuthStore authStore;

    @BeforeEach
    void setUp() {
        // mock 的 execute 默认返回 null，等价于共享版本号不存在（干净 Redis）。
        authStore = new AuthStore(redisTemplate);
    }

    @Test
    void storesAndRemovesPermissionSnapshot() {
        authStore.put(USER_ID, Set.of("admin:work:audit"));

        assertTrue(authStore.has(USER_ID, "admin:work:audit"));
        assertFalse(authStore.has(USER_ID, "page/work/work.html"));

        authStore.remove(USER_ID);
        assertFalse(authStore.has(USER_ID, "admin:work:audit"));
    }

    @Test
    void invalidateAllClearsLoadedSnapshots() {
        authStore.put(USER_ID, Set.of("admin:work:audit"));
        assertTrue(authStore.has(USER_ID, "admin:work:audit"));

        authStore.invalidateAll();
        assertFalse(authStore.has(USER_ID, "admin:work:audit"));
    }

    @Test
    void globalSwitchCanDisableChecks() {
        authStore.disable();

        assertFalse(authStore.enabled());
    }
}
