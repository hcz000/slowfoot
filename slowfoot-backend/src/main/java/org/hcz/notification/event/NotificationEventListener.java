package org.hcz.notification.event;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.hcz.notification.service.NotificationService;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = NotificationMessaging.TOPIC,
        selectorExpression = "*",
        consumerGroup = "${rocketmq.consumer.groups.notification}")
public class NotificationEventListener implements RocketMQListener<NotificationMessage> {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void onMessage(NotificationMessage message) {
        notificationService.persist(new NotificationEvent(
                this,
                message.getEventId(),
                message.getUserId(),
                message.getActorId(),
                message.getType(),
                message.getTargetId(),
                message.getWorkId(),
                message.getContent()));
    }
}
