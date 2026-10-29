package org.hcz.common.messaging.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hcz.common.messaging.entity.EventOutbox;
import org.hcz.common.messaging.mapper.EventOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;
// 事件出箱服务
@Service
public class EventOutboxService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final EventOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    public EventOutboxService(EventOutboxMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(String eventId, String eventType, String topic, String tag, Object message) {
        final String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize outbox event " + eventType, e);
        }

        EventOutbox row = new EventOutbox();
        row.setEventId(eventId == null ? UUID.randomUUID().toString() : eventId);
        row.setEventType(eventType);
        row.setTopic(topic);
        row.setTag(tag);
        row.setPayload(payload);
        row.setStatus(EventOutbox.PENDING);
        row.setRetryCount(0);
        row.setNextRetryAt(new Date());
        row.setCreatedAt(new Date());
        row.setUpdatedAt(new Date());
        mapper.insert(row);
    }

    public String errorMessage(Throwable error) {
        String message = error == null ? "unknown outbox delivery failure" : error.getMessage();
        if (message == null || message.isBlank()) {
            message = error == null ? "unknown outbox delivery failure" : error.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    public void logDead(EventOutbox row, Throwable error) {
        org.slf4j.LoggerFactory.getLogger(EventOutboxService.class).atError()
                .addKeyValue("event_category", "event_outbox")
                .addKeyValue("event_action", "dead_letter")
                .addKeyValue("event_outcome", "failure")
                .addKeyValue("outbox_id", row.getId())
                .addKeyValue("event_id", row.getEventId())
                .addKeyValue("event_type", row.getEventType())
                .addKeyValue("topic", row.getTopic())
                .addKeyValue("retry_count", row.getRetryCount())
                .addKeyValue("error_type", error == null ? "Unknown" : error.getClass().getSimpleName())
                .log("Event outbox moved to dead letter: {}", errorMessage(error), error);
    }
}

