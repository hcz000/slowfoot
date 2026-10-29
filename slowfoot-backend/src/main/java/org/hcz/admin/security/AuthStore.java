package org.hcz.admin.security;

import org.hcz.common.constant.RedisKeys;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户权限快照缓存。
 *
 * <p>本地 Map 只加速当前实例；权限变更后通过 Redis 全局版本号让所有实例失效本地快照，
 * 避免多实例部署时其他节点继续放行旧权限。Redis 不可用时退回本地快照（读多写少的
 * 权限信息允许短暂陈旧，撤销操作仍会在下一次版本同步后生效）。</p>
 */
@Component
public final class AuthStore {

    private static final byte[] VERSION_KEY =
            RedisKeys.AUTH_PERMISSION_VERSION.getBytes(StandardCharsets.UTF_8);

    private final Map<Long, Set<String>> permissions = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;

    private volatile boolean enabled = true;
    private volatile long localVersion = -1L;

    public AuthStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public boolean enabled() {
        return enabled;
    }

    public void put(Long userId, Collection<String> userPermissions) {
        if (userId == null) {
            return;
        }
        Set<String> snapshot = userPermissions == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(userPermissions));
        permissions.put(userId, snapshot);
    }

    public void remove(Long userId) {
        if (userId != null) {
            permissions.remove(userId);
        }
    }

    public boolean has(Long userId, String permission) {
        syncSharedVersion();
        Set<String> userPermissions = permissions.get(userId);
        return userPermissions != null && permission != null && userPermissions.contains(permission);
    }

    /**
     * 权限变更后调用：递增 Redis 版本号并失效本地快照，
     * 其他实例在下一次 {@link #has} 校验时自动丢弃过期的权限缓存。
     */
    public void invalidateAll() {
        try {
            Long version = redisTemplate.execute((RedisCallback<Long>) connection ->
                    connection.incr(VERSION_KEY));
            permissions.clear();
            if (version != null) {
                localVersion = version;
            }
        } catch (DataAccessException e) {
            // Redis 故障时至少保证本实例不再放行旧权限；其他实例在恢复同步后失效。
            permissions.clear();
            localVersion = -1L;
        }
    }

    private void syncSharedVersion() {
        final long current = localVersion;
        long shared;
        try {
            final byte[] raw = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                    connection.get(VERSION_KEY));
            shared = raw == null ? 0L : parseVersion(raw);
        } catch (DataAccessException e) {
            // Redis 不可用时退回本地快照，不阻断请求。
            return;
        }
        if (current < 0) {
            // 首次校验只记录基线版本，不清理本实例已加载的快照。
            localVersion = shared;
            return;
        }
        if (shared != current) {
            permissions.clear();
            localVersion = shared;
        }
    }

    private long parseVersion(byte[] raw) {
        try {
            return Long.parseLong(new String(raw, StandardCharsets.UTF_8));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
