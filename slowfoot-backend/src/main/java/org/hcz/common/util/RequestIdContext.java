package org.hcz.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.regex.Pattern;


// Resolves 该 请求 ID shared by logs, headers, 并 API responses.

public final class RequestIdContext {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = RequestIdContext.class.getName();
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestIdContext() {
    }

    public static String current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        Object requestId = attributes.getRequest().getAttribute(ATTRIBUTE_NAME);
        return requestId == null ? null : requestId.toString();
    }

    public static String resolve(HttpServletRequest request) {
        String requestId = request.getHeader(HEADER_NAME);
        return isValid(requestId) ? requestId : UUID.randomUUID().toString();
    }

    public static boolean isValid(String requestId) {
        return requestId != null && SAFE_REQUEST_ID.matcher(requestId).matches();
    }
}
