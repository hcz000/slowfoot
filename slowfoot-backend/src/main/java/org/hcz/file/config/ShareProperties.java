package org.hcz.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


// 分享链接配置。注意 videoPath 指向的是前端路由，不是后端 API 路径。

@Data
@Component
@ConfigurationProperties(prefix = "share")
public class ShareProperties {


// 前端部署的公开地址。

    private String frontendBaseUrl = "http://localhost:8882";


// 前端展示作品的路由前缀（不是 API 路径）。

    private String videoPath = "/video/";

    public String buildShareUrl(Long workId) {
        String baseUrl = trimTrailingSlash(frontendBaseUrl.trim());
        String path = videoPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return baseUrl + path + workId;
    }

    private String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
