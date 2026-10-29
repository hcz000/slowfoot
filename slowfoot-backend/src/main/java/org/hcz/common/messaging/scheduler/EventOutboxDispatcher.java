package org.hcz.common.messaging.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.hcz.common.messaging.entity.EventOutbox;
import org.hcz.comment.event.CommentAuditSubmittedMessage;
import org.hcz.notification.event.NotificationMessage;
import org.hcz.audit.event.WorkAuditCompletedMessage;
import org.hcz.audit.event.WorkAuditSubmittedMessage;
import org.hcz.work.event.WorkInteractionMessage;
import org.hcz.common.messaging.mapper.EventOutboxMapper;
import org.hcz.common.messaging.service.EventOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Claims and publishes durable events. Delivery is at-least-once; consumers
 * must use eventId as their idempotency key.
 */
@Component
public class EventOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventOutboxDispatcher.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 8;
    private static final long LOCK_MILLIS = 60_000L;
    private static final long RETRY_BASE_MILLIS = 1_000L;

    private final EventOutboxMapper mapper;
    private final EventOutboxService outboxService;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public EventOutboxDispatcher(EventOutboxMapper mapper,
                                 EventOutboxService outboxService,
                                 RocketMQTemplate rocketMQTemplate,
                                 ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.outboxService = outboxService;
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${event-outbox.dispatch-delay-ms:1000}")
    public void dispatch() {
        Date now = new Date();
        List<EventOutbox> rows = mapper.findDispatchable(now, BATCH_SIZE);
        for (EventOutbox row : rows) {
            if (mapper.claim(row.getId(), now, new Date(now.getTime() + LOCK_MILLIS)) != 1) {
                continue;
            }
            deliver(row);
        }
    }

    private void deliver(EventOutbox row) {
        try {
            Object message = objectMapper.readValue(row.getPayload(), messageClass(row.getEventType()));
            String destination = row.getTopic() + (row.getTag() == null || row.getTag().isBlank()
                    ? "" : ":" + row.getTag());
            rocketMQTemplate.syncSend(destination, message);
            mapper.markSent(row.getId());
        } catch (Exception error) {
            int retries = row.getRetryCount() == null ? 1 : row.getRetryCount();
            boolean dead = retries >= MAX_RETRIES;
            long delay = RETRY_BASE_MILLIS * (1L << Math.min(Math.max(retries - 1, 0), 10));
            Date nextRetry = dead ? new Date() : new Date(System.currentTimeMillis() + delay);
            mapper.markFailure(row.getId(), dead ? EventOutbox.DEAD : EventOutbox.PENDING,
                    nextRetry, outboxService.errorMessage(error));
            if (dead) {
                outboxService.logDead(row, error);
            } else {
                log.atWarn()
                        .addKeyValue("event_category", "event_outbox")
                        .addKeyValue("event_action", "delivery_retry")
                        .addKeyValue("event_outcome", "failure")
                        .addKeyValue("outbox_id", row.getId())
                        .addKeyValue("event_id", row.getEventId())
                        .addKeyValue("event_type", row.getEventType())
                        .addKeyValue("retry_count", retries)
                        .addKeyValue("next_retry_at", nextRetry)
                        .addKeyValue("error_type", error.getClass().getSimpleName())
                        .log("Event outbox delivery failed: {}", outboxService.errorMessage(error), error);
            }
        }
    }

    private Class<?> messageClass(String eventType) {
        return switch (eventType) {
            case "WORK_INTERACTION" -> WorkInteractionMessage.class;
            case "NOTIFICATION" -> NotificationMessage.class;
            case "COMMENT_AUDIT_SUBMITTED" -> CommentAuditSubmittedMessage.class;
            case "WORK_AUDIT_SUBMITTED" -> WorkAuditSubmittedMessage.class;
            case "WORK_AUDIT_COMPLETED" -> WorkAuditCompletedMessage.class;
            default -> throw new IllegalArgumentException("Unknown outbox event type: " + eventType);
        };
    }
}

