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

import java.util.HashSet;
import java.util.List;
import java.util.Set;


// 图片作品适配器，支持多张图片并保留传入顺序。

@Service
public class ImageWorkContentAdapter implements WorkContentAdapter {

    private final FileService fileService;

    public ImageWorkContentAdapter(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public WorkType type() {
        return WorkType.IMAGE;
    }

    @Override
    public void validate(WorkDraft draft, Long ownerId) {
        if (draft == null || draft.type() != WorkType.IMAGE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "作品类型不是图片");
        }
        if (draft.assets().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "图片作品至少需要一张图片");
        }
        // 同一文件在同一作品中只能出现一次，对应 work_asset 的 uk_work_asset_resource。
        // 先于归属查询判断，重复请求不必访问文件表。
        Set<Long> seenFileIds = new HashSet<>();
        for (WorkAsset asset : draft.assets()) {
            if (asset.assetType() != WorkAssetType.IMAGE) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "图片作品只能包含图片文件");
            }
            if (!seenFileIds.add(asset.fileId())) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "第 " + (asset.sortOrder() + 1) + " 张图片与前面的图片重复");
            }
            File image = fileService.getOwnedFile(asset.fileId(), ownerId);
            if (image.getFormat() == null || !image.getFormat().startsWith("image/")) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "作品资源必须是图片文件");
            }
        }
    }

    @Override
    public List<WorkAsset> assetsForAudit(WorkDraft draft) {
        validateType(draft);
        return draft.assets();
    }

    private void validateType(WorkDraft draft) {
        if (draft == null || draft.type() != WorkType.IMAGE) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "作品类型不是图片");
        }
    }
}
