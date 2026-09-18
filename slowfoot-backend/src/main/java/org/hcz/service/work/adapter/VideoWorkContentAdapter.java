package org.hcz.service.work.adapter;

import org.hcz.entity.File;
import org.hcz.entity.work.WorkAsset;
import org.hcz.entity.work.WorkAssetType;
import org.hcz.entity.work.WorkDraft;
import org.hcz.entity.work.WorkType;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.service.file.FileService;
import org.springframework.stereotype.Service;

import java.util.List;


// 视频作品适配器，封装视频源和封面文件的校验规则。

@Service
public class VideoWorkContentAdapter implements WorkContentAdapter {

    private final FileService fileService;

    public VideoWorkContentAdapter(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public WorkType type() {
        return WorkType.VIDEO;
    }

    @Override
    public void validate(WorkDraft draft, Long ownerId) {
        requireType(draft);
        List<WorkAsset> videoAssets = draft.assets().stream()
                .filter(asset -> asset.assetType() == WorkAssetType.VIDEO)
                .toList();
        if (videoAssets.size() != 1) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "视频作品必须且只能有一个视频文件");
        }

        File video = fileService.getOwnedFile(videoAssets.get(0).fileId(), ownerId);
        requireFormat(video, "video/", "视频源必须是视频文件");

        List<WorkAsset> coverAssets = draft.assets().stream()
                .filter(asset -> asset.assetType() == WorkAssetType.COVER)
                .toList();
        if (coverAssets.size() > 1) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "视频作品最多只能有一个封面");
        }
        if (!coverAssets.isEmpty()) {
            File cover = fileService.getOwnedFile(coverAssets.get(0).fileId(), ownerId);
            requireFormat(cover, "image/", "视频封面必须是图片文件");
        }
    }

    @Override
    public List<WorkAsset> assetsForAudit(WorkDraft draft) {
        requireType(draft);
        return draft.assets();
    }

    private void requireType(WorkDraft draft) {
        if (draft == null || draft.type() != WorkType.VIDEO) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "作品类型不是视频");
        }
    }

    private void requireFormat(File file, String prefix, String message) {
        if (file == null || file.getFormat() == null || !file.getFormat().startsWith(prefix)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, message);
        }
    }
}
