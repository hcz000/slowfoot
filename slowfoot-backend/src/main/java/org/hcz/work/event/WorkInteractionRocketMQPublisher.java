package org.hcz.work.event;

import org.hcz.common.messaging.service.EventOutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WorkInteractionRocketMQPublisher {

    private final EventOutboxService outbox;
    private final String topic;

    public WorkInteractionRocketMQPublisher(EventOutboxService outbox,
                                            @Value("${rocketmq.topics.work-interaction}") String topic) {
        this.outbox = outbox;
        this.topic = topic;
    }

    @EventListener
    public void handle(WorkInteractionEvent event) {
        outbox.enqueue(event.getEventId(), "WORK_INTERACTION", topic,
                WorkInteractionMessaging.INTERACTION_TAG, WorkInteractionMessage.from(event));
    }
}
