package org.hcz.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.code.kaptcha.Producer;
import org.hcz.common.constant.RedisKeys;
import org.hcz.auth.entity.Captcha;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.auth.mapper.CaptchaMapper;
import org.hcz.auth.service.CaptchaService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.auth.service.EmailService;
import org.hcz.common.util.DateUtil;
import org.hcz.common.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.Date;


// 系统验证码 服务实现类

@Service
public class CaptchaServiceImpl extends ServiceImpl<CaptchaMapper, Captcha> implements CaptchaService {


    @Autowired
    private Producer producer;

    @Autowired
    private EmailService emailService;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Override
    public BufferedImage getCaptcha(String uuId) {
        String code = this.producer.createText();
        Captcha captcha = new Captcha();
        captcha.setUuid(uuId);
        captcha.setCode(code);
        captcha.setExpireTime(DateUtil.addDateMinutes(new Date(),5));
        this.save(captcha);
        return producer.createImage(code);
    }

    @Override
    public boolean validate(Captcha captcha) throws Exception {
        String email = captcha.getEmail();
        final String code1 = captcha.getCode();
        captcha = this.getOne(new LambdaQueryWrapper<Captcha>().eq(Captcha::getUuid, captcha.getUuid()));
        if (captcha == null) throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码不存在或已过期");

        this.remove(new LambdaQueryWrapper<Captcha>().eq(Captcha::getUuid, captcha.getUuid()));
        if(!captcha.getCode().equals(code1)){
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码不正确");
        }
        if(captcha.getExpireTime().getTime()<=System.currentTimeMillis()){
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码已过期");
        }
        if (!code1.equals(captcha.getCode())){
            return false;
        }

        String code = getSixCode();
        redisCacheUtil.set(RedisKeys.EMAIL_CODE+email,code,RedisKeys.EMAIL_CODE_TIME);
        emailService.send(email,"注册验证码:"+code+",验证码5分钟之内有效");
        return true;
    }


    public static String getSixCode(){
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int code = (int) (Math.random()*10);
            builder.append(code);
        }
        return builder.toString();
    }

    @Override
    public boolean verifyCaptcha(String uuid, String code) {
        Captcha captcha = this.getOne(
                new LambdaQueryWrapper<Captcha>().eq(Captcha::getUuid, uuid));
        if (captcha == null) {
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码不存在或已过期");
        }
        this.remove(new LambdaQueryWrapper<Captcha>().eq(Captcha::getUuid, uuid));
        if (captcha.getExpireTime().getTime() <= System.currentTimeMillis()) {
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码已过期");
        }
        if (!captcha.getCode().equalsIgnoreCase(code)) {
            throw new AppException(ErrorCode.CAPTCHA_INVALID, "验证码不正确");
        }
        return true;
    }
}
