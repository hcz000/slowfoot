package org.hcz.work.service.adapter;

import org.hcz.work.entity.WorkAsset;
import org.hcz.work.entity.WorkDraft;
import org.hcz.work.entity.WorkType;

import java.util.List;


// 负责处理不同作品类型的资源校验和审核资源选择。

public interface WorkContentAdapter {

    WorkType type();

    void validate(WorkDraft draft, Long ownerId);

    List<WorkAsset> assetsForAudit(WorkDraft draft);
}
