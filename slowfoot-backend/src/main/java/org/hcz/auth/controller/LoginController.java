package org.hcz.auth.controller;

import org.hcz.auth.entity.Captcha;
import org.hcz.user.entity.User;
import org.hcz.auth.dto.FindPWVO;
import org.hcz.auth.dto.LoginVO;
import org.hcz.auth.dto.RegisterVO;
import org.hcz.auth.dto.RefreshTokenVO;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.common.security.SecurityAuditLogger;
import org.hcz.common.security.access.Limit;
import org.hcz.auth.service.LoginService;
import org.hcz.user.service.UserService;
import org.hcz.common.util.JwtUtils;
import org.hcz.common.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;




@RestController
@RequestMapping("/slowfoot/login")
public class LoginController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserService userService;

    @Autowired
    private SecurityAuditLogger securityAuditLogger;


// 登录
// @param loginVO
// @return

    @PostMapping
    @Limit(limit = 10, time = 60L, key = "login:ip", msg = "登录请求过于频繁，请1分钟后再试")
    public ApiResponse login(@RequestBody @Validated LoginVO loginVO){
        User user;
        try {
            user = loginService.login(loginVO);
        } catch (AppException exception) {
            securityAuditLogger.authenticationFailed("login", null, exception.getErrorCode().name());
            throw exception;
        }
        securityAuditLogger.authenticationSucceeded("login", user.getId());
        // 登录成功，生成token
        return ApiResponse.ok().data(tokenResponse(user));
    }

    @PostMapping("/refresh")
    public ApiResponse refresh(@RequestBody @Validated RefreshTokenVO request) {
        Long userId = JwtUtils.getRefreshUserId(request.getRefreshToken());
        if (userId == null) {
            securityAuditLogger.authenticationFailed("token_refresh", null, "invalid_refresh_token");
            throw new AppException(ErrorCode.UNAUTHENTICATED, "刷新令牌无效或已过期");
        }
        User user = userService.getById(userId);
        if (ObjectUtils.isEmpty(user)) {
            securityAuditLogger.authenticationFailed("token_refresh", userId, "user_not_found");
            throw new AppException(ErrorCode.UNAUTHENTICATED, "用户不存在");
        }
        securityAuditLogger.authenticationSucceeded("token_refresh", userId);
        return ApiResponse.ok().data(tokenResponse(user));
    }

    private HashMap<Object, Object> tokenResponse(User user) {
        final HashMap<Object, Object> map = new HashMap<>();
        map.put("token", JwtUtils.getAccessToken(user.getId(), user.getNickName()));
        map.put("refreshToken", JwtUtils.getRefreshToken(user.getId()));
        map.put("name", user.getNickName());
        map.put("user", user);
        return map;
    }



    // 获取验证码
    @GetMapping("/captcha.jpg/{uuId}")
    public void captcha(HttpServletResponse response, @PathVariable String uuId) throws IOException {
        loginService.captcha(uuId,response);
    }

    // 验证验证码

    @PostMapping("/getCode")
    public ApiResponse getCode(@RequestBody @Validated Captcha captcha) throws Exception {
        if (!loginService.getCode(captcha)) {
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码错误");
        }
        return ApiResponse.ok().message("验证码正确");
    }



    // 验证邮箱和验证码
    @PostMapping("/check")
    public ApiResponse check(String email,Integer code){
        loginService.checkCode(email,code);
        return ApiResponse.ok().message("验证成功");
    }


// 注册
// @param registerVO
// @return
// @throws Exception

    @PostMapping("/register")
    public ApiResponse register(@RequestBody @Validated RegisterVO registerVO) throws Exception {
        if (!loginService.register(registerVO)) {
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "注册失败，验证码错误");
        }
        securityAuditLogger.accountChanged("register", null);
        return ApiResponse.ok().message("注册成功");
    }


// 找回密码
// @param findPWVO
// @return

    @PostMapping("/findPassword")
    public ApiResponse findPassword(@RequestBody @Validated FindPWVO findPWVO,HttpServletResponse response){
        final Boolean b = loginService.findPassword(findPWVO);
        if (!b) {
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码不正确");
        }
        securityAuditLogger.accountChanged("password_reset", null);
        return ApiResponse.ok().message("修改成功");
    }

}
