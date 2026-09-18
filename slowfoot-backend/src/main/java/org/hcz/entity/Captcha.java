package org.hcz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 验证码
@Data
@EqualsAndHashCode(callSuper = false)
public class Captcha implements Serializable {

    private static final long serialVersionUID = 1L;


// 验证码 UUID

    @NotBlank(message = "uuid为空")
    private String uuid;


// 验证码

    @NotBlank(message = "code为空")
    private String code;


// 过期时间

    private Date expireTime;

    @TableField(exist = false)
    @Email
    private String email;

}
