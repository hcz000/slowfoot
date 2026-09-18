package org.hcz.service.work.adapter;

import org.hcz.entity.work.WorkAsset;
import org.hcz.entity.work.WorkDraft;
import org.hcz.entity.work.WorkType;

import java.util.List;


// 负责处理不同作品类型的资源校验和审核资源选择。

public interface WorkContentAdapter {

    WorkType type();

    void validate(WorkDraft draft, Long ownerId);

    List<WorkAsset> assetsForAudit(WorkDraft draft);
}
