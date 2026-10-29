package org.hcz.work.entity;

import java.util.ArrayList;
import java.util.List;


// Unified draft 用于  validate 作品 content before 正在发布.

public record WorkDraft(WorkType type, List<WorkAsset> assets, String textContent) {


// 保留 该 existing constructor 用于 media drafts.

    public WorkDraft(WorkType type, List<WorkAsset> assets) {
        this(type, assets, null);
    }

    public WorkDraft {
        if (type == null) {
            throw new IllegalArgumentException("work type cannot be null");
        }
        if (assets == null) {
            throw new IllegalArgumentException("work assets cannot be null");
        }
        if (type != WorkType.TEXT && assets.isEmpty()) {
            throw new IllegalArgumentException("media work must contain at least one asset");
        }
        if (type == WorkType.TEXT && (textContent == null || textContent.isBlank())) {
            throw new IllegalArgumentException("text work content cannot be blank");
        }
        if (type != WorkType.TEXT && textContent != null && !textContent.isBlank()) {
            throw new IllegalArgumentException("media work cannot contain text content");
        }
        assets = List.copyOf(assets);
    }

    public static WorkDraft video(Long videoFileId, Long coverFileId) {
        List<WorkAsset> assets = new ArrayList<>();
        assets.add(new WorkAsset(videoFileId, WorkAssetType.VIDEO, 0));
        if (coverFileId != null) {
            assets.add(new WorkAsset(coverFileId, WorkAssetType.COVER, 0));
        }
        return new WorkDraft(WorkType.VIDEO, assets, null);
    }

    public static WorkDraft images(List<Long> imageFileIds) {
        if (imageFileIds == null || imageFileIds.isEmpty()) {
            throw new IllegalArgumentException("image work must contain at least one image");
        }
        List<WorkAsset> assets = new ArrayList<>();
        for (int i = 0; i < imageFileIds.size(); i++) {
            assets.add(new WorkAsset(imageFileIds.get(i), WorkAssetType.IMAGE, i));
        }
        return new WorkDraft(WorkType.IMAGE, assets, null);
    }

    public static WorkDraft text(String textContent) {
        return new WorkDraft(WorkType.TEXT, List.of(), textContent);
    }
}
