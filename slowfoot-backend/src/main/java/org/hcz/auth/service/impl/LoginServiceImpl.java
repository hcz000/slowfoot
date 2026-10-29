package org.hcz.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.common.constant.RedisKeys;
import org.hcz.auth.entity.Captcha;
import org.hcz.user.entity.User;
import org.hcz.auth.dto.FindPWVO;
import org.hcz.auth.dto.LoginVO;
import org.hcz.auth.dto.RegisterVO;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.auth.service.CaptchaService;
import org.hcz.auth.service.LoginService;
import org.hcz.user.service.UserService;
import org.hcz.common.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.imageio.ImageIO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;



@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User login(LoginVO loginVO) {
        String email = loginVO.getEmail();

        // 1. 检查账号是否被锁定
        String lockKey = RedisKeys.LOGIN_LOCK + email;
        if (redisCacheUtil.hasKey(lockKey)) {
            long ttl = redisCacheUtil.getExpire(lockKey);
            throw new AppException(ErrorCode.RATE_LIMITED,
                    "账号已被临时锁定，请" + ttl + "秒后重试");
        }

        // 2. 验证图形验证码
        captchaService.verifyCaptcha(loginVO.getUuid(), loginVO.getCaptchaCode());

        // 3. 查询用户并校验密码
        final String password = loginVO.getPassword();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        User user = userService.getOne(wrapper.eq(User::getEmail, email));
        if (ObjectUtils.isEmpty(user)) {
            recordLoginFailure(email);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordLoginFailure(email);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 4. 登录成功，清除失败计数
        redisCacheUtil.del(RedisKeys.LOGIN_FAIL + email);
        return user;
    }

    private void recordLoginFailure(String email) {
        String failKey = RedisKeys.LOGIN_FAIL + email;
        Long count = redisCacheUtil.incr(failKey, 1);
        if (count != null && count == 1) {
            redisCacheUtil.expire(failKey, RedisKeys.LOGIN_LOCK_DURATION);
        }
        if (count != null && count >= RedisKeys.LOGIN_MAX_FAILURES) {
            redisCacheUtil.set(RedisKeys.LOGIN_LOCK + email, "locked", RedisKeys.LOGIN_LOCK_DURATION);
            redisCacheUtil.del(failKey);
        }
    }

    @Override
    public Boolean checkCode(String email, Integer code) {
        if (ObjectUtils.isEmpty(email) || ObjectUtils.isEmpty(code)){
            throw new AppException(ErrorCode.VALIDATION_ERROR, "邮箱和验证码不能为空");
        }
        final Object o = redisCacheUtil.get(RedisKeys.EMAIL_CODE + email);

        if (!code.toString().equals(o)){
            throw new AppException(ErrorCode.CAPTCHA_INVALID);

        }
        return true;
    }

    @Override
    public void captcha(String uuId, HttpServletResponse response) throws IOException {
        if (ObjectUtils.isEmpty(uuId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "验证码标识不能为空");
        }
        response.setHeader("Cache-Control", "no-store, no-cache");
        response.setContentType("image/jpeg");
        BufferedImage image = captchaService.getCaptcha(uuId);
        try (ServletOutputStream out = response.getOutputStream()) {
            ImageIO.write(image, "jpg", out);
        }
    }

    @Override
    public Boolean getCode(Captcha captcha) throws Exception {

        return captchaService.validate(captcha);
    }

    @Override
    public Boolean register(RegisterVO registerVO) throws Exception {
        // 注册成功后删除图形验证码
        if (userService.register(registerVO)){
            captchaService.removeById(registerVO.getUuid());
            return true;
        }
        return false;
    }

    @Override
    public Boolean findPassword(FindPWVO findPWVO) {
        final Boolean b = userService.findPassword(findPWVO);
        return b;
    }
}
