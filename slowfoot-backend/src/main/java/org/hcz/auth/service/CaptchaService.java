package org.hcz.auth.service;

import org.hcz.auth.entity.Captcha;
import com.baomidou.mybatisplus.extension.service.IService;

import java.awt.image.BufferedImage;


// 系统验证码 服务类
//

public interface CaptchaService extends IService<Captcha> {


    BufferedImage getCaptcha(String uuId);

    boolean validate(Captcha captcha) throws Exception;

    boolean verifyCaptcha(String uuid, String code);
}
