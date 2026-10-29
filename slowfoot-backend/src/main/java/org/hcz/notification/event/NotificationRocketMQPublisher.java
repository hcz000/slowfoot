package org.hcz.notification.event;

import org.hcz.common.messaging.service.EventOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationRocketMQPublisher {

    private final EventOutboxService outbox;
    private final String topic;

    public NotificationRocketMQPublisher(EventOutboxService outbox,
                                         @Value("${rocketmq.topics.notification}") String topic) {
        this.outbox = outbox;
        this.topic = topic;
    }

    @EventListener
    public void handle(NotificationEvent event) {
        outbox.enqueue(event.getEventId(), "NOTIFICATION", topic, null,
                NotificationMessage.from(event));
    }
}
