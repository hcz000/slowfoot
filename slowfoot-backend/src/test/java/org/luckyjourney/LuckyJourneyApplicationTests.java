package org.luckyjourney;

import org.hcz.HczApplication;
import org.junit.jupiter.api.Test;

class LuckyJourneyApplicationTests {

    @Test
    void applicationHasAnEntryPoint() throws NoSuchMethodException {
        HczApplication.class.getMethod("main", String[].class);
    }
}
