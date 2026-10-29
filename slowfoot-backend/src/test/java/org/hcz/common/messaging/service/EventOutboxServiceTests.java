package org.hcz.common.messaging.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hcz.common.messaging.entity.EventOutbox;
import org.hcz.common.messaging.mapper.EventOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventOutboxServiceTests {

    @Mock
    private EventOutboxMapper mapper;

    private EventOutboxService service;

    @BeforeEach
    void setUp() {
        service = new EventOutboxService(mapper, new ObjectMapper());
    }

    @Test
    void enqueuePersistsSerializedPendingEvent() {
        service.enqueue("event-1", "NOTIFICATION", "notification-topic", "created",
                new NotificationPayload(42L, "A work was liked"));

        ArgumentCaptor<EventOutbox> row = ArgumentCaptor.forClass(EventOutbox.class);
        verify(mapper).insert(row.capture());
        assertEquals("event-1", row.getValue().getEventId());
        assertEquals("NOTIFICATION", row.getValue().getEventType());
        assertEquals("notification-topic", row.getValue().getTopic());
        assertEquals("created", row.getValue().getTag());
        assertEquals("{\"userId\":42,\"content\":\"A work was liked\"}", row.getValue().getPayload());
        assertEquals(EventOutbox.PENDING, row.getValue().getStatus());
        assertEquals(0, row.getValue().getRetryCount());
        assertNotNull(row.getValue().getNextRetryAt());
        assertNotNull(row.getValue().getCreatedAt());
        assertNotNull(row.getValue().getUpdatedAt());
    }

    @Test
    void enqueueGeneratesAnEventIdWhenNoneIsProvided() {
        service.enqueue(null, "NOTIFICATION", "notification-topic", null, "payload");

        ArgumentCaptor<EventOutbox> row = ArgumentCaptor.forClass(EventOutbox.class);
        verify(mapper).insert(row.capture());
        assertNotNull(row.getValue().getEventId());
        assertEquals(row.getValue().getEventId(), UUID.fromString(row.getValue().getEventId()).toString());
        assertEquals("\"payload\"", row.getValue().getPayload());
    }

    @Test
    void enqueueDoesNotPersistWhenPayloadSerializationFails() {
        assertThrows(IllegalStateException.class,
                () -> service.enqueue("event-1", "NOTIFICATION", "notification-topic", null, new Object()));

        verify(mapper, never()).insert(any(EventOutbox.class));
    }

    @Test
    void errorMessageUsesFallbackTextAndCapsStoredLength() {
        assertEquals("unknown outbox delivery failure", service.errorMessage(null));
        assertEquals("IllegalArgumentException", service.errorMessage(new IllegalArgumentException()));
        assertTrue(service.errorMessage(new IllegalStateException("x".repeat(501))).length() <= 500);
    }

    private record NotificationPayload(Long userId, String content) {
    }
}
