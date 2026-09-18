package org.hcz.service.file.impl;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.hcz.config.AliyunOssProperties;
import org.hcz.entity.vo.CloudFileInfo;
import org.hcz.entity.vo.OssUploadTicket;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.service.file.AliyunOssFileService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.Date;


// OSS 文件操作实现，业务层只处理 objectKey。

@Service
public class AliyunOssFileServiceImpl implements AliyunOssFileService {

    private final AliyunOssProperties ossProperties;

    public AliyunOssFileServiceImpl(AliyunOssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    @Override
    public OssUploadTicket createUploadTicket(String type, String filename) {
        try {
            final String contentType = ossProperties.resolveContentType(type, filename);
            final String objectKey = ossProperties.buildObjectKey(contentType, filename);
            final URL uploadUrl = generateUploadUrl(objectKey, contentType);
            return new OssUploadTicket(uploadUrl.toString(), objectKey, contentType,
                    ossProperties.getUploadUrlExpireSeconds(), HttpMethod.PUT.name(),
                    ossProperties.maxSizeBytes(contentType));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

    @Override
    public String uploadFile(File file) {
        if (file == null || !file.isFile()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "文件不存在");
        }
        final String contentType;
        try {
            contentType = Files.probeContentType(file.toPath());
        } catch (Exception e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "无法识别文件类型", e);
        }
        if (contentType == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "无法识别文件类型");
        }
        final String objectKey = ossProperties.buildObjectKey(contentType, file.getName());
        final OSS client = ossProperties.buildClient();
        try {
            client.putObject(new PutObjectRequest(ossProperties.getBucketName(), objectKey, file));
            return objectKey;
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        final String key = baseObjectKey(objectKey);
        final OSS client = ossProperties.buildClient();
        try {
            client.deleteObject(ossProperties.getBucketName(), key);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public CloudFileInfo getFileInfo(String objectKey) {
        final String key = baseObjectKey(objectKey);
        final OSS client = ossProperties.buildClient();
        try {
            final ObjectMetadata metadata = client.getObjectMetadata(ossProperties.getBucketName(), key);
            return new CloudFileInfo(metadata.getContentType(), metadata.getContentLength());
        } catch (RuntimeException e) {
            return null;
        } finally {
            client.shutdown();
        }
    }

    @Override
    public String getAccessUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return objectKey;
        }
        final String[] resource = splitResource(objectKey);
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                ossProperties.getBucketName(), resource[0], HttpMethod.GET);
        request.setExpiration(new Date(System.currentTimeMillis()
                + ossProperties.getPrivateUrlExpireSeconds() * 1000L));
        if (resource[1] != null) {
            request.addQueryParameter("x-oss-process", resource[1]);
        }
        final OSS client = ossProperties.buildClient();
        try {
            return client.generatePresignedUrl(request).toString();
        } finally {
            client.shutdown();
        }
    }

    private URL generateUploadUrl(String objectKey, String contentType) {
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                ossProperties.getBucketName(), objectKey, HttpMethod.PUT);
        request.setExpiration(new Date(System.currentTimeMillis()
                + ossProperties.getUploadUrlExpireSeconds() * 1000L));
        request.setContentType(contentType);
        final OSS client = ossProperties.buildClient();
        try {
            return client.generatePresignedUrl(request);
        } finally {
            client.shutdown();
        }
    }

    private String baseObjectKey(String objectKey) {
        return splitResource(objectKey)[0];
    }

    private String[] splitResource(String objectKey) {
        final int queryIndex = objectKey.indexOf('?');
        if (queryIndex < 0) {
            return new String[]{objectKey, null};
        }
        final String query = objectKey.substring(queryIndex + 1);
        final String process = query.startsWith("x-oss-process=")
                ? query.substring("x-oss-process=".length()) : null;
        return new String[]{objectKey.substring(0, queryIndex), process};
    }
}
