package org.hcz.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("security.audit");
    private final HttpServletRequest request;

    public SecurityAuditLogger(HttpServletRequest request) {
        this.request = request;
    }

    public void authenticationSucceeded(String action, Long userId) {
        write(log.atInfo(), "authentication", action, "success", userId, null, null, null);
    }

    public void authenticationFailed(String action, Long userId, String reason) {
        write(log.atWarn(), "authentication", action, "failure", userId, reason, null, null);
    }

    public void accountChanged(String action, Long userId) {
        write(log.atInfo(), "account", action, "success", userId, null, null, null);
    }

    public void authorizationDenied(Long userId, String reason, String permission) {
        write(log.atWarn(), "authorization", "access_denied", "failure",
                userId, reason, permission, null);
    }

    public void rateLimitExceeded(Long userId, String bucket) {
        write(log.atWarn(), "security", "rate_limit_exceeded", "failure",
                userId, "rate_limit_exceeded", null, bucket);
    }

    private void write(LoggingEventBuilder event, String category, String action, String outcome,
                       Long userId, String reason, String permission, String bucket) {
        event.addKeyValue("event_category", category)
                .addKeyValue("event_action", action)
                .addKeyValue("event_outcome", outcome)
                .addKeyValue("source_ip", request.getRemoteAddr())
                .addKeyValue("http_method", request.getMethod())
                .addKeyValue("url_path", request.getRequestURI());
        if (userId != null) {
            event.addKeyValue("user_id", userId);
        }
        if (reason != null) {
            event.addKeyValue("security_reason", reason);
        }
        if (permission != null) {
            event.addKeyValue("security_permission", permission);
        }
        if (bucket != null) {
            event.addKeyValue("rate_limit_bucket", bucket);
        }
        event.log("Security audit event");
    }
}
