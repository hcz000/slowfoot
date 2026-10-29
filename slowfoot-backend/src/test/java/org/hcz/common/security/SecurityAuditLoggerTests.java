package org.hcz.common.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditLoggerTests {

    private final Logger logger = (Logger) LoggerFactory.getLogger("security.audit");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void emitsQueryableFieldsWithoutCredentials() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/slowfoot/login");
        request.setRemoteAddr("192.0.2.10");
        appender.start();
        logger.addAppender(appender);

        new SecurityAuditLogger(request).authenticationFailed("login", null, "INVALID_CREDENTIALS");

        ILoggingEvent event = appender.list.getFirst();
        Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Security audit event");
        assertThat(fields.keySet()).contains("event_category", "event_action", "event_outcome",
                "source_ip", "http_method", "url_path", "security_reason");
        assertThat(fields.keySet()).doesNotContain("password", "token", "cookie", "request_body");
        assertThat(fields.values()).contains("authentication", "login", "failure", "192.0.2.10", "POST",
                "/slowfoot/login", "INVALID_CREDENTIALS");
    }
}
