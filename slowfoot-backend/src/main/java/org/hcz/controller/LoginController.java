package org.hcz.controller;

import org.hcz.entity.Captcha;
import org.hcz.entity.user.User;
import org.hcz.entity.vo.FindPWVO;
import org.hcz.entity.vo.RegisterVO;
import org.hcz.entity.vo.RefreshTokenVO;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.security.SecurityAuditLogger;
import org.hcz.service.auth.LoginService;
import org.hcz.service.user.UserService;
import org.hcz.util.JwtUtils;
import org.hcz.util.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;




@RestController
@RequestMapping("/luckyjourney/login")
public class LoginController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserService userService;

    @Autowired
    private SecurityAuditLogger securityAuditLogger;


// 登录
// @param user
// @return

    @PostMapping
    public ApiResponse login(@RequestBody @Validated User user){
        try {
            user = loginService.login(user);
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


// 鑾峰彇鍥惧舰楠岃瘉鐮?
// @param response
// @param uuId
// @throws IOException

    @GetMapping("/captcha.jpg/{uuId}")
    public void captcha(HttpServletResponse response, @PathVariable String uuId) throws IOException {
        loginService.captcha(uuId,response);
    }


// 鑾峰彇楠岃瘉鐮?
// @param captcha
// @return
// @throws Exception

    @PostMapping("/getCode")
    public ApiResponse getCode(@RequestBody @Validated Captcha captcha) throws Exception {
        if (!loginService.getCode(captcha)) {
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码错误");
        }
        return ApiResponse.ok().message("鍙戦€佹垚鍔?璇疯€愬績绛夊緟");
    }


// 妫€娴嬮偖绠遍獙璇佺爜
// @param email
// @param code
// @return

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
