package org.hcz.audit.event;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.hcz.work.entity.Work;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.feed.service.FeedService;
import org.hcz.common.messaging.service.EventConsumeService;
import org.hcz.recommendation.service.InterestPushService;
import org.hcz.user.service.FollowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;

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
    private final EventConsumeService consumeService;

    public WorkAuditCompletedEventListener(FeedService feedService,
                                           InterestPushService interestPushService,
                                           FollowService followService,
                                           WorkMapper workMapper,
                                           EventConsumeService consumeService) {
        this.feedService = feedService;
        this.interestPushService = interestPushService;
        this.followService = followService;
        this.workMapper = workMapper;
        this.consumeService = consumeService;
    }

    @Override
    public void onMessage(WorkAuditCompletedMessage event) {
        final String consumer = "audit-completed";
        try {
            Work work = workMapper.selectById(event.getWorkId());
            if (work == null) {
                consumeService.markConsumed(event.getEventId(), consumer);
                return;
            }
            if (event.isApprovedForPublish()) {
                interestPushService.pushSystemTypeStockInSync(work);
                interestPushService.pushSystemStockInSync(work);
                feedService.pusOutBoxFeedSync(event.getUserId(), event.getWorkId(), event.getCreatedTime());
            } else if (event.isRemoveFromFeed()) {
                interestPushService.deleteSystemStockInSync(work);
                interestPushService.deleteSystemTypeStockInSync(work);
                Collection<Long> fans = followService.getFans(event.getUserId(), null);
                feedService.deleteOutBoxFeedSync(event.getUserId(), fans, event.getWorkId());
            }
            consumeService.markConsumed(event.getEventId(), consumer);
        } catch (Exception e) {
            log.error("Work audit side effect failed, workId={}, eventId={}",
                    event.getWorkId(), event.getEventId(), e);
            throw new IllegalStateException("Work audit side effect failed", e);
        }
    }
}
