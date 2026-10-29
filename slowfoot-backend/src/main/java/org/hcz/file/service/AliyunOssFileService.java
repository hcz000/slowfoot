package org.hcz.file.service;

import org.hcz.file.dto.CloudFileInfo;
import org.hcz.file.dto.MultipartCompleteRequest;
import org.hcz.file.dto.MultipartInitResult;
import org.hcz.file.dto.MultipartPartUrl;
import org.hcz.file.dto.OssUploadTicket;

import java.io.File;


// 阿里云 OSS 文件服务。

public interface AliyunOssFileService extends FileCloudService {

    OssUploadTicket createUploadTicket(String type, String filename);

    String uploadFile(File file);

    /**
     * 将对象从 sourceKey 复制到 destKey（同 bucket 内）。
     * 用于注册时从 temp/ 迁移到正式路径。
     */
    void copyObject(String sourceKey, String destKey);

    void deleteFile(String objectKey);

    CloudFileInfo getFileInfo(String objectKey);

    String getAccessUrl(String objectKey);

    // --- 分片上传 ---

    /**
     * 初始化分片上传，返回 uploadId 和 objectKey。
     */
    MultipartInitResult initiateMultipartUpload(String type, String filename);

    /**
     * 为单个分片生成预签名 PUT URL。
     */
    MultipartPartUrl generatePartUrl(String objectKey, String uploadId, int partNumber);

    /**
     * 完成分片上传：合并所有分片，返回最终 objectKey。
     */
    String completeMultipartUpload(MultipartCompleteRequest request);

    /**
     * 中止分片上传，清理已上传的分片。
     */
    void abortMultipartUpload(String objectKey, String uploadId);
}
