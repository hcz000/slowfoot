package org.hcz.work.service.interaction;

import org.hcz.work.entity.WorkShare;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;



public interface WorkShareService extends IService<WorkShare> {


// 添加分享记录
// @param workShare

    boolean share(WorkShare workShare);


// 获取分享用户id
// @param workId
// @return

    List<Long> getShareUserIds(Long workId);
}
