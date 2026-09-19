package org.hcz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hcz.util.RequestIdContext;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


// 为每个 HTTP 请求及其响应添加可追踪的请求 ID。

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = RequestIdContext.resolve(request);
        request.setAttribute(RequestIdContext.ATTRIBUTE_NAME, requestId);
        response.setHeader(RequestIdContext.HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            logCompletedRequest(request, response, startedAt);
            MDC.remove(MDC_KEY);
        }
    }

    private void logCompletedRequest(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        int status = response.getStatus();
        LoggingEventBuilder event = status >= 500 ? log.atError()
                : status >= 400 ? log.atWarn() : log.atInfo();
        event.addKeyValue("event_category", "http")
                .addKeyValue("event_action", "request_completed")
                .addKeyValue("http_method", request.getMethod())
                .addKeyValue("url_path", request.getRequestURI())
                .addKeyValue("http_status", status)
                .addKeyValue("duration_ms", (System.nanoTime() - startedAt) / 1_000_000)
                .addKeyValue("source_ip", request.getRemoteAddr())
                .log("HTTP request completed");
    }
}
