package org.hcz.service.file.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.entity.File;
import org.hcz.entity.vo.CloudFileInfo;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.mapper.file.FileMapper;
import org.hcz.service.file.FileService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.service.file.AliyunOssFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;



@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements FileService {

    @Autowired
    private AliyunOssFileService ossFileService;

    @Override
    public Long save(String fileKey,Long userId) {

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
        final CloudFileInfo videoFileInfo = ossFileService.getFileInfo(fileKey);

        if (videoFileInfo == null){
            throw new AppException(ErrorCode.VALIDATION_ERROR, "文件参数不正确");
        }

        final File videoFile = new File();
        String type = videoFileInfo.getContentType();
        if (type == null || (!type.startsWith("video/") && !type.startsWith("image/"))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "仅支持图片或视频文件");
        }
        videoFile.setFileKey(fileKey);
        videoFile.setFormat(type);
        videoFile.setType(type.contains("video") ? "视频" : "图片");
        videoFile.setUserId(userId);
        videoFile.setSize(videoFileInfo.getSize());
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
        File file = getOwnedFile(fileId, userId);
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
