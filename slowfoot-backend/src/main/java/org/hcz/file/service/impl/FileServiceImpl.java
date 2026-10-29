package org.hcz.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.file.config.AliyunOssProperties;
import org.hcz.file.entity.File;
import org.hcz.file.dto.CloudFileInfo;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.file.mapper.FileMapper;
import org.hcz.file.service.FileService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.file.service.AliyunOssFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;



@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    @Autowired
    private AliyunOssFileService ossFileService;

    @Autowired
    private AliyunOssProperties ossProperties;

    @Override
    public Long save(String fileKey, Long userId) {

        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        final File existingFile = getOne(new LambdaQueryWrapper<File>().eq(File::getFileKey, fileKey));
        if (existingFile != null) {
            if (!userId.equals(existingFile.getUserId())) {
                throw new AppException(ErrorCode.FORBIDDEN, "无权关联该文件");
            }
            return existingFile.getId();
        }

        // 判断文件
        final CloudFileInfo cloudFileInfo = ossFileService.getFileInfo(fileKey);

        if (cloudFileInfo == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "文件参数不正确");
        }

        final String contentType = cloudFileInfo.getContentType();
        if (contentType == null || (!contentType.startsWith("video/") && !contentType.startsWith("image/"))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "仅支持图片或视频文件");
        }

        // 从 temp/ 复制到正式路径
        final String finalKey = ossProperties.toFinalKey(fileKey);
        if (!finalKey.equals(fileKey)) {
            ossFileService.copyObject(fileKey, finalKey);
            log.debug("Copied OSS object from temp: {} -> {}", fileKey, finalKey);
        }

        final File videoFile = new File();
        videoFile.setFileKey(finalKey);
        videoFile.setFormat(contentType);
        videoFile.setType(contentType.contains("video") ? "视频" : "图片");
        videoFile.setUserId(userId);
        videoFile.setSize(cloudFileInfo.getSize());
        save(videoFile);

        return videoFile.getId();
    }

    @Override
    public Long generatePhoto(Long fileId,Long userId) {
        final File file = getOwnedFile(fileId, userId);
        if (!file.getFormat().startsWith("video/")) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "仅视频文件可以生成封面");
        }
        final String fileKey = file.getFileKey() + "?x-oss-process=video/snapshot,t_1000,f_jpg";
        final File fileInfo = new File();
        fileInfo.setFileKey(fileKey);
        fileInfo.setFormat("image/*");
        fileInfo.setType("图片");
        fileInfo.setUserId(userId);
        save(fileInfo);
        return fileInfo.getId();
    }

    @Override
    public File getFileTrustUrl(Long fileId, Long userId) {
        // 播放/展示用的信任地址：已发布的视频、封面、头像属于公开素材，
        // 需允许任意登录用户（乃至作者本人以外的人）访问，因此这里不校验归属。
        if (fileId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "文件参数不正确");
        }
        File file = getById(fileId);
        if (file == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "未找到该文件");
        }
        file.setFileKey(ossFileService.getAccessUrl(file.getFileKey()));
        return file;
    }

    @Override
    public File getOwnedFile(Long fileId, Long userId) {
        if (fileId == null || userId == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "文件参数不正确");
        }
        File file = getById(fileId);
        if (file == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "未找到该文件");
        }
        if (!userId.equals(file.getUserId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "无权访问该文件");
        }
        return file;
    }
}
