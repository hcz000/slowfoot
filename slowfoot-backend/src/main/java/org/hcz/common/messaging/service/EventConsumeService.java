package org.hcz.common.messaging.service;

import org.hcz.common.messaging.mapper.EventConsumeMapper;
import org.springframework.stereotype.Service;

/**
 * Shared eventId + consumer marker used by RocketMQ consumers.
 *
 * <p>Database consumers insert the marker in the same transaction as their
 * database changes. Consumers with idempotent Redis effects write the marker
 * after those effects, so a crash before completion remains retryable.</p>
 */
@Service
public class EventConsumeService {

    private final EventConsumeMapper mapper;

    public EventConsumeService(EventConsumeMapper mapper) {
        this.mapper = mapper;
    }

    public boolean tryClaim(String eventId, String consumer) {
        return eventId != null && mapper.insertIgnore(eventId, consumer) == 1;
    }

    /** Persist completion after an idempotent side effect has succeeded. */
    public boolean markConsumed(String eventId, String consumer) {
        return eventId != null && mapper.insertIgnore(eventId, consumer) == 1;
    }

    public void release(String eventId, String consumer) {
        if (eventId != null) {
            mapper.delete(eventId, consumer);
        }
    }
}

