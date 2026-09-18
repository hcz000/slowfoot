package org.hcz.service.file;

import org.hcz.entity.File;
import com.baomidou.mybatisplus.extension.service.IService;

public interface FileService extends IService<File> {


    Long save(String fileKey,Long userId);


// 根据视频id生成图片
// @param fileId
// @return

    Long generatePhoto(Long fileId,Long userId);


// 获取文件真实URL
// @param fileId 文件id
// @return

    File getFileTrustUrl(Long fileId, Long userId);


// 获取当前用户拥有的文件。

    File getOwnedFile(Long fileId, Long userId);
}
