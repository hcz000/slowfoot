package org.hcz.work.event;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.hcz.common.constant.AuditStatus;
import org.hcz.work.dto.WorkRankItem;
import org.hcz.work.entity.Work;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.recommendation.scheduler.HotRankScheduler;
import org.hcz.common.messaging.service.EventConsumeService;
import org.hcz.work.service.interaction.HotRankCache;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = WorkInteractionMessaging.TOPIC,
        selectorExpression = WorkInteractionMessaging.INTERACTION_TAG,
        consumerGroup = "${rocketmq.consumer.groups.work-hot}")
public class WorkHotEventListener implements RocketMQListener<WorkInteractionMessage> {

    private final WorkMapper workMapper;
    private final HotRankCache hotRankCache;
    private final EventConsumeService consumeService;

    public WorkHotEventListener(WorkMapper workMapper,
                                HotRankCache hotRankCache,
                                EventConsumeService consumeService) {
        this.workMapper = workMapper;
        this.hotRankCache = hotRankCache;
        this.consumeService = consumeService;
    }

    @Override
    public void onMessage(WorkInteractionMessage event) {
        final String consumer = "work-hot";
        try {
            Work work = workMapper.selectById(event.getWorkId());
            if (work == null) {
                consumeService.markConsumed(event.getEventId(), consumer);
                return;
            }
            if (!Integer.valueOf(AuditStatus.SUCCESS.code()).equals(work.getAuditStatus())
                    || Boolean.TRUE.equals(work.getOpenState())) {
                hotRankCache.removeWork(event.getWorkId());
                consumeService.markConsumed(event.getEventId(), consumer);
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
            consumeService.markConsumed(event.getEventId(), consumer);
        } catch (RuntimeException e) {
            throw e;
        }
    }

    private double value(Number number) {
        return number == null ? 0.0D : number.doubleValue();
    }
}
