package org.hcz.messaging.workinteraction;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkInteractionRocketMQPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public WorkInteractionRocketMQPublisher(
            RocketMQTemplate rocketMQTemplate,
            @Value("${rocketmq.topics.work-interaction}") String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(WorkInteractionEvent event) {
        rocketMQTemplate.syncSend(topic + ":" + WorkInteractionMessaging.INTERACTION_TAG,
                WorkInteractionMessage.from(event));
    }
}
