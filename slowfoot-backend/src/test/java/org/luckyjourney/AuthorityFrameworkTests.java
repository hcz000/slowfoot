package org.luckyjourney;

import org.hcz.security.access.AuthStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorityFrameworkTests {

    private static final long USER_ID = 9001L;

    @AfterEach
    void clearState() {
        AuthStore.remove(USER_ID);
        AuthStore.enable();
    }

    @Test
    void storesAndRemovesPermissionSnapshot() {
        AuthStore.put(USER_ID, Set.of("admin:work:audit"));

        assertTrue(AuthStore.has(USER_ID, "admin:work:audit"));
        assertFalse(AuthStore.has(USER_ID, "page/work/work.html"));

        AuthStore.remove(USER_ID);
        assertFalse(AuthStore.has(USER_ID, "admin:work:audit"));
    }

    @Test
    void globalSwitchCanDisableChecks() {
        AuthStore.disable();

        assertFalse(AuthStore.enabled());
    }
}
