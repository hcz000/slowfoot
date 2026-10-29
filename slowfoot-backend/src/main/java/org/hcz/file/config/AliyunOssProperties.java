package org.hcz.file.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;


// 阿里云 OSS 配置和对象地址工具。

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    private String endpoint;
    private String region;
    private String publicDomain;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private long uploadUrlExpireSeconds = 300L;
    private long privateUrlExpireSeconds = 300L;
    private long maxImageSizeBytes = 10L * 1024 * 1024;
    private long maxVideoSizeBytes = 500L * 1024 * 1024;

    public OSS buildClient() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }


// 解析上传文件的内容类型：优先使用显式传入的类型（MIME，或 video/image 分类，或后缀），
// 其次根据文件名后缀推断。无法识别时抛出明确的参数异常。

    public String resolveContentType(String type, String filename) {
        final String fromType = canonicalContentType(type);
        if (fromType != null) {
            return fromType;
        }
        final String fromFilename = canonicalContentType(extension(filename));
        if (fromFilename != null) {
            return fromFilename;
        }
        throw new IllegalArgumentException("无法识别文件类型，请指定 type 或使用带后缀的图片/视频文件");
    }


// 根据内容类型返回允许上传的最大字节数。

    public long maxSizeBytes(String contentType) {
        return contentType.startsWith("video/") ? maxVideoSizeBytes : maxImageSizeBytes;
    }

    private String canonicalContentType(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        final String value = candidate.trim().toLowerCase();
        if (value.startsWith("video/") || value.startsWith("image/")) {
            return value;
        }
        switch (value) {
            case "video":
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "mkv":
                return "video/x-matroska";
            case "webm":
                return "video/webm";
            case "image":
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            default:
                return null;
        }
    }

    /**
     * 构建临时上传路径（temp/ 前缀），注册时 copy 到正式路径。
     * OSS 生命周期规则自动清理 temp/ 下超过 N 天的对象。
     */
    public String buildObjectKey(String contentType, String filename) {
        final String folder = contentType.startsWith("video/") ? "videos" : "images";
        String extension = extension(filename);
        if (extension.isBlank()) {
            extension = contentType.startsWith("video/") ? "mp4" : "jpg";
        }
        return "temp/" + folder + "/" + UUID.randomUUID() + "." + extension;
    }

    /**
     * 根据 temp 路径推导正式存储路径（去掉 temp/ 前缀）。
     */
    public String toFinalKey(String tempKey) {
        if (tempKey != null && tempKey.startsWith("temp/")) {
            return tempKey.substring("temp/".length());
        }
        return tempKey;
    }

    public String buildPublicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return objectKey;
        }
        final String base = publicDomain == null || publicDomain.isBlank()
                ? publicBaseUrl()
                : publicDomain.replaceFirst("/$", "");
        return base + "/" + objectKey;
    }

    public String publicBaseUrl() {
        if (publicDomain != null && !publicDomain.isBlank()) {
            return publicDomain.replaceFirst("/$", "");
        }
        return endpoint.replaceFirst("^(https?://)", "$1" + bucketName + ".")
                .replaceFirst("/$", "");
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        final int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }
}
