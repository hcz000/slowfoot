package org.hcz.common.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.hcz.common.util.RequestIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterLoggingTests {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @AfterEach
    void cleanUp() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void logsRequestSummaryWithCorrelationIdAndNoSensitiveRequestData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/slowfoot/login");
        request.setRemoteAddr("192.0.2.20");
        request.addHeader(RequestIdContext.HEADER_NAME, "request-456");
        request.addHeader("Authorization", "Bearer secret-token");
        request.setQueryString("password=secret-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(401);
        appender.start();
        logger.addAppender(appender);

        new RequestIdFilter().doFilterInternal(request, response, chain);

        ILoggingEvent event = appender.list.getFirst();
        Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-456");
        assertThat(fields).containsEntry("event_category", "http")
                .containsEntry("event_action", "request_completed")
                .containsEntry("http_method", "POST")
                .containsEntry("url_path", "/slowfoot/login")
                .containsEntry("http_status", 401)
                .containsEntry("source_ip", "192.0.2.20")
                .containsKey("duration_ms");
        assertThat(event.toString()).doesNotContain("secret-token", "secret-password", "Authorization");
        assertThat(MDC.get("requestId")).isNull();
    }
}
