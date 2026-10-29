package org.hcz.file.service.impl;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import org.hcz.file.config.AliyunOssProperties;
import org.hcz.file.dto.CloudFileInfo;
import org.hcz.file.dto.MultipartCompleteRequest;
import org.hcz.file.dto.MultipartInitResult;
import org.hcz.file.dto.MultipartPartUrl;
import org.hcz.file.dto.OssUploadTicket;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.file.service.AliyunOssFileService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


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
    public void copyObject(String sourceKey, String destKey) {
        final OSS client = ossProperties.buildClient();
        try {
            client.copyObject(new CopyObjectRequest(
                    ossProperties.getBucketName(), sourceKey,
                    ossProperties.getBucketName(), destKey));
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

    // --- 分片上传实现 ---

    @Override
    public MultipartInitResult initiateMultipartUpload(String type, String filename) {
        try {
            final String contentType = ossProperties.resolveContentType(type, filename);
            final String objectKey = ossProperties.buildObjectKey(contentType, filename);
            final InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(
                    ossProperties.getBucketName(), objectKey);
            final ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            request.setObjectMetadata(metadata);

            final OSS client = ossProperties.buildClient();
            try {
                final InitiateMultipartUploadResult result = client.initiateMultipartUpload(request);
                return new MultipartInitResult(result.getUploadId(), objectKey);
            } finally {
                client.shutdown();
            }
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

    @Override
    public MultipartPartUrl generatePartUrl(String objectKey, String uploadId, int partNumber) {
        final GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                ossProperties.getBucketName(), objectKey, HttpMethod.PUT);
        request.setExpiration(new Date(System.currentTimeMillis()
                + ossProperties.getUploadUrlExpireSeconds() * 1000L));
        request.addQueryParameter("uploadId", uploadId);
        request.addQueryParameter("partNumber", String.valueOf(partNumber));

        final OSS client = ossProperties.buildClient();
        try {
            final URL url = client.generatePresignedUrl(request);
            return new MultipartPartUrl(partNumber, url.toString());
        } finally {
            client.shutdown();
        }
    }

    @Override
    public String completeMultipartUpload(MultipartCompleteRequest req) {
        final List<PartETag> partETags = new ArrayList<>();
        for (MultipartCompleteRequest.PartETagInfo info : req.getParts()) {
            partETags.add(new PartETag(info.getPartNumber(), info.getETag()));
        }
        // OSS 要求分片按 partNumber 升序
        partETags.sort((a, b) -> Integer.compare(a.getPartNumber(), b.getPartNumber()));

        final CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                ossProperties.getBucketName(), req.getObjectKey(), req.getUploadId(), partETags);

        final OSS client = ossProperties.buildClient();
        try {
            client.completeMultipartUpload(request);
            return req.getObjectKey();
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void abortMultipartUpload(String objectKey, String uploadId) {
        final AbortMultipartUploadRequest request = new AbortMultipartUploadRequest(
                ossProperties.getBucketName(), objectKey, uploadId);
        final OSS client = ossProperties.buildClient();
        try {
            client.abortMultipartUpload(request);
        } finally {
            client.shutdown();
        }
    }
}
