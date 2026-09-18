package org.hcz.authority;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthStore {

    private static final Map<Long, Set<String>> PERMISSIONS = new ConcurrentHashMap<>();
    private static volatile boolean enabled = true;

    private AuthStore() {
    }

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void put(Long userId, Collection<String> permissions) {
        if (userId == null) {
            return;
        }
        Set<String> snapshot = permissions == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(permissions));
        PERMISSIONS.put(userId, snapshot);
    }

    public static void remove(Long userId) {
        if (userId != null) {
            PERMISSIONS.remove(userId);
        }
    }

    public static boolean has(Long userId, String permission) {
        Set<String> permissions = PERMISSIONS.get(userId);
        return permissions != null && permission != null && permissions.contains(permission);
    }
}
