package org.hcz.service.file;

import org.hcz.entity.vo.CloudFileInfo;
import org.hcz.entity.vo.OssUploadTicket;

import java.io.File;


// 阿里云 OSS 文件服务。

public interface AliyunOssFileService extends FileCloudService {

    OssUploadTicket createUploadTicket(String type, String filename);

    String uploadFile(File file);

    void deleteFile(String objectKey);

    CloudFileInfo getFileInfo(String objectKey);

    String getAccessUrl(String objectKey);
}
