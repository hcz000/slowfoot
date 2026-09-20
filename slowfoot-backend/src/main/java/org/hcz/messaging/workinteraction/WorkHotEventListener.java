package org.hcz.messaging.workinteraction;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.hcz.constant.AuditStatus;
import org.hcz.entity.vo.WorkRankItem;
import org.hcz.entity.work.Work;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.schedul.HotRankScheduler;
import org.hcz.service.work.interaction.HotRankCache;
import org.springframework.stereotype.Component;


// 作品互动热度事件监听器，负责实时更新 Redis 排行榜。

@Component
@RocketMQMessageListener(
        topic = WorkInteractionMessaging.TOPIC,
        selectorExpression = WorkInteractionMessaging.INTERACTION_TAG,
        consumerGroup = "${rocketmq.consumer.groups.work-hot}")
public class WorkHotEventListener implements RocketMQListener<WorkInteractionMessage> {

    private final WorkMapper workMapper;
    private final HotRankCache hotRankCache;

    public WorkHotEventListener(WorkMapper workMapper, HotRankCache hotRankCache) {
        this.workMapper = workMapper;
        this.hotRankCache = hotRankCache;
    }


// 事务提交后重新读取作品统计值，再写入排行榜。

    @Override
    public void onMessage(WorkInteractionMessage event) {
        Work work = workMapper.selectById(event.getWorkId());
        if (work == null) {
            return;
        }
        if (!Integer.valueOf(AuditStatus.SUCCESS.code()).equals(work.getAuditStatus())
                || Boolean.TRUE.equals(work.getOpenState())) {
            hotRankCache.removeWork(event.getWorkId());
            return;
        }

        double weight = value(work.getShareCount())
                + value(work.getViewCount()) * 0.8D
                + value(work.getLikeCount())
                + value(work.getFavoriteCount()) * 1.5D;
        double hot = HotRankScheduler.hot(weight, HotRankScheduler.ageInDays(work.getGmtCreated()));
        WorkRankItem rankItem = new WorkRankItem(hot, work.getId(), work.getTitle());
        rankItem.setHot(null);

        hotRankCache.upsert(rankItem, hot);
    }

    private double value(Number number) {
        return number == null ? 0.0D : number.doubleValue();
    }
}
