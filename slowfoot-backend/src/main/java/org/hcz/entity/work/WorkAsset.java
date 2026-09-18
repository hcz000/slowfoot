package org.hcz.entity.work;


// 作品关联的文件资源。sortOrder 用于多图片作品的展示顺序。

public record WorkAsset(Long fileId, WorkAssetType assetType, int sortOrder) {

    public WorkAsset {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("作品文件不能为空");
        }
        if (assetType == null) {
            throw new IllegalArgumentException("作品文件类型不能为空");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("作品文件顺序不能小于 0");
        }
    }
}
