package org.hcz.config;

import com.p6spy.engine.spy.appender.CustomLineFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class P6SpyConfigurationTests {

    @Test
    void routesOnlyParameterSafeSlowSqlThroughSlf4j() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/spy.properties")) {
            assertThat(input).isNotNull();
            properties.load(input);
        }

        assertThat(properties.getProperty("appender"))
                .isEqualTo("com.p6spy.engine.spy.appender.Slf4JLogger");
        assertThat(properties.getProperty("executionThreshold")).isEqualTo("2000");
        assertThat(properties.getProperty("outagedetection")).isEqualTo("false");
        assertThat(properties.getProperty("excludecategories")).contains("batch").doesNotContain("batc,");

        String format = properties.getProperty("customLogMessageFormat");
        assertThat(format).contains("%(effectiveSqlSingleLine)");
        assertThat(format).doesNotContain("%(sql)", "%(sqlSingleLine)");
    }

    @Test
    void formatterDoesNotExposeBoundValues() {
        String message = new CustomLineFormat().formatMessage(
                1,
                "2026-01-01 00:00:00",
                2100,
                "statement",
                "select * from users where password = ?",
                "select * from users where password = 'secret-value'",
                "jdbc:mysql://localhost/test");

        assertThat(message).contains("password = ?", "sql_duration_ms=2100");
        assertThat(message).doesNotContain("secret-value");
    }
}
