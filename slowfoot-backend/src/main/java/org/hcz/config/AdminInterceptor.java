package org.hcz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hcz.entity.user.User;
import org.hcz.exception.ErrorCode;
import org.hcz.security.SecurityAuditLogger;
import org.hcz.util.UserHolder;
import org.hcz.service.user.UserService;
import org.hcz.util.JwtUtils;
import org.hcz.util.ApiResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    private ObjectMapper objectMapper = new ObjectMapper();

    private UserService userService;
    private final SecurityAuditLogger securityAuditLogger;

   public AdminInterceptor(UserService userService, SecurityAuditLogger securityAuditLogger){
       this.userService = userService;
       this.securityAuditLogger = securityAuditLogger;
   }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserHolder.clear();

        if (request.getMethod().equals("OPTIONS")){
            return true;
        }

        if (!JwtUtils.checkAccessToken(request)) {
            securityAuditLogger.authenticationFailed("access_token", null, "invalid_access_token");
            response(ApiResponse.error(ErrorCode.UNAUTHENTICATED).message("请登录后再操作"), response,
                    ErrorCode.UNAUTHENTICATED.getHttpStatus().value());
            return false;
        }

        final Long userId = JwtUtils.getUserId(request);
        final User user = userService.getById(userId);
        if (ObjectUtils.isEmpty(user)){
            securityAuditLogger.authenticationFailed("access_token", userId, "user_not_found");
            response(ApiResponse.error(ErrorCode.UNAUTHENTICATED).message("用户不存在"), response,
                    ErrorCode.UNAUTHENTICATED.getHttpStatus().value());
            return false;
        }
        UserHolder.set(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserHolder.clear();
    }

    private boolean response(ApiResponse<?> r, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Cache-Control", "no-cache");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().println(objectMapper.writeValueAsString(r));
        response.getWriter().flush();
        return false;
    }
}
