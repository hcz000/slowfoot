package org.hcz.util;

import org.hcz.config.AliyunOssProperties;
import org.springframework.stereotype.Component;


// 为实体对象生成 OSS 公共访问地址。

@Component
public class OssUrlUtil {

    private static volatile String publicBaseUrl;

    public OssUrlUtil(AliyunOssProperties ossProperties) {
        publicBaseUrl = ossProperties.publicBaseUrl();
    }

    public static String buildUrl(Object objectKey) {
        if (objectKey == null) {
            return null;
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return objectKey.toString();
        }
        return publicBaseUrl + "/" + objectKey;
    }
}
