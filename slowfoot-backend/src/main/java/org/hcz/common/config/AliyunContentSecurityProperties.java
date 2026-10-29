package org.hcz.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


// 阿里云内容安全配置。

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.content-security")
public class AliyunContentSecurityProperties {

    private String region = "cn-shanghai";
    private String accessKeyId;
    private String accessKeySecret;
}
