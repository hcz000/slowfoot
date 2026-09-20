package org.hcz.messaging.notification;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationRocketMQPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public NotificationRocketMQPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${rocketmq.topics.notification}") String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(NotificationEvent event) {
        rocketMQTemplate.syncSend(topic, NotificationMessage.from(event));
    }
}
