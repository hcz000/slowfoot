package org.luckyjourney;

import org.junit.jupiter.api.Test;
import org.hcz.util.TextUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextUtilsTests {

    @Test
    void truncatePassesThroughNullAndShortValues() {
        assertNull(TextUtils.truncate(null, 500));
        assertEquals("abc", TextUtils.truncate("abc", 3));
        assertEquals("abc", TextUtils.truncate("abc", 500));
    }

    @Test
    void truncateCutsToTheRequestedLength() {
        assertEquals("abc", TextUtils.truncate("abcdef", 3));
    }

    @Test
    void truncateKeepsSurrogatePairsIntact() {
        String emoji = "😀";

        assertEquals("a", TextUtils.truncate("a" + emoji, 2));
        assertEquals("a" + emoji, TextUtils.truncate("a" + emoji, 3));
    }
}
