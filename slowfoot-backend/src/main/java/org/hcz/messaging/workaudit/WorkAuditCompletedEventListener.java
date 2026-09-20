package org.hcz.messaging.workaudit;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.hcz.entity.work.Work;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.service.feed.FeedService;
import org.hcz.service.recommendation.InterestPushService;
import org.hcz.service.user.FollowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;


// 作品审核完成后的推流副作用，三种作品类型共用。
//
// 用 WorkMapper 而不是 WorkService：审核服务不应反向依赖作品 facade。
// 副作用失败只记日志，绝不回滚或影响主流程。

@Component
@RocketMQMessageListener(
        topic = WorkAuditMessaging.COMPLETED_TOPIC,
        selectorExpression = "*",
        consumerGroup = "${rocketmq.consumer.groups.audit-completed}")
public class WorkAuditCompletedEventListener implements RocketMQListener<WorkAuditCompletedMessage> {

    private static final Logger log = LoggerFactory.getLogger(WorkAuditCompletedEventListener.class);

    private final FeedService feedService;
    private final InterestPushService interestPushService;
    private final FollowService followService;
    private final WorkMapper workMapper;

    public WorkAuditCompletedEventListener(FeedService feedService,
                                           InterestPushService interestPushService,
                                           FollowService followService,
                                           WorkMapper workMapper) {
        this.feedService = feedService;
        this.interestPushService = interestPushService;
        this.followService = followService;
        this.workMapper = workMapper;
    }

    @Override
    public void onMessage(WorkAuditCompletedMessage event) {
        try {
            Work work = workMapper.selectById(event.getWorkId());
            if (work == null) {
                return;
            }
            if (event.isApprovedForPublish()) {
                interestPushService.pushSystemTypeStockIn(work);
                interestPushService.pushSystemStockIn(work);
                feedService.pusOutBoxFeed(event.getUserId(), event.getWorkId(), event.getCreatedTime());
            } else if (event.isRemoveFromFeed()) {
                interestPushService.deleteSystemStockIn(work);
                interestPushService.deleteSystemTypeStockIn(work);
                Collection<Long> fans = followService.getFans(event.getUserId(), null);
                feedService.deleteOutBoxFeed(event.getUserId(), fans, event.getWorkId());
            }
        } catch (Exception e) {
            log.error("Work audit side effect failed, workId={}, eventId={}",
                    event.getWorkId(), event.getEventId(), e);
            throw new IllegalStateException("Work audit side effect failed", e);
        }
    }
}
