package org.hcz.work.service.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkLike;
import org.hcz.work.entity.WorkShare;
import org.hcz.recommendation.service.InterestPushService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


// 作品删除后的互动数据清理。
//
// 单独成 Bean 是必须的：@异步 依赖代理，WorkModerationService.deleteMine 内部自调用不会走代理，
// 注解会静默失效并把清理拖回删除事务里。原先这里是一个裸 new Thread。

@Service
public class WorkInteractionCleanupService {

    private final WorkLikeService workLikeService;
    private final WorkShareService workShareService;
    private final InterestPushService interestPushService;

    public WorkInteractionCleanupService(WorkLikeService workLikeService,
                                        WorkShareService workShareService,
                                        InterestPushService interestPushService) {
        this.workLikeService = workLikeService;
        this.workShareService = workShareService;
        this.interestPushService = interestPushService;
    }

    @Async("recommendationEventExecutor")
    public void cleanup(Long workId, Long userId, Work work) {
        // 删除调用者留下的分享量与点赞量
        workShareService.remove(new LambdaQueryWrapper<WorkShare>()
                .eq(WorkShare::getWorkId, workId).eq(WorkShare::getUserId, userId));
        workLikeService.remove(new LambdaQueryWrapper<WorkLike>()
                .eq(WorkLike::getWorkId, workId).eq(WorkLike::getUserId, userId));
        interestPushService.deleteSystemStockIn(work);
        interestPushService.deleteSystemTypeStockIn(work);
    }
}
