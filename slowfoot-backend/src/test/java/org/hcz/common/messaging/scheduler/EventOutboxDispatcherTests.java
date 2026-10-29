package org.hcz.common.messaging.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.hcz.common.messaging.entity.EventOutbox;
import org.hcz.common.messaging.mapper.EventOutboxMapper;
import org.hcz.notification.event.NotificationMessage;
import org.hcz.common.messaging.service.EventOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventOutboxDispatcherTests {

    private static final long ROW_ID = 17L;
    private static final String TOPIC = "notification-topic";

    @Mock
    private EventOutboxMapper mapper;

    @Mock
    private EventOutboxService outboxService;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EventOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new EventOutboxDispatcher(mapper, outboxService, rocketMQTemplate, objectMapper);
    }

    @Test
    void publishesClaimedNotificationWithTagAndMarksItSent() throws Exception {
        NotificationMessage message = new NotificationMessage(
                "event-1", 42L, 7L, "LIKE", 91L, 91L, "A work was liked");
        EventOutbox row = row("NOTIFICATION", TOPIC, "created", objectMapper.writeValueAsString(message), 0);
        dispatchable(row, 1);

        dispatcher.dispatch();

        ArgumentCaptor<NotificationMessage> sentMessage = ArgumentCaptor.forClass(NotificationMessage.class);
        InOrder order = inOrder(rocketMQTemplate, mapper);
        order.verify(rocketMQTemplate).syncSend(eq(TOPIC + ":created"), sentMessage.capture());
        assertEquals(message.getEventId(), sentMessage.getValue().getEventId());
        assertEquals(message.getUserId(), sentMessage.getValue().getUserId());
        order.verify(mapper).markSent(ROW_ID);
        verify(mapper, never()).markFailure(eq(ROW_ID), any(Integer.class), any(Date.class), any(String.class));
    }

    @Test
    void skipsRowsThatAnotherDispatcherAlreadyClaimed() {
        EventOutbox row = row("NOTIFICATION", TOPIC, null, "{}", 0);
        dispatchable(row, 0);

        dispatcher.dispatch();

        verifyNoInteractions(rocketMQTemplate, outboxService);
        verify(mapper, never()).markSent(ROW_ID);
        verify(mapper, never()).markFailure(eq(ROW_ID), any(Integer.class), any(Date.class), any(String.class));
    }

    @Test
    void schedulesFailedDeliveryForRetryAndStoresTheError() throws Exception {
        NotificationMessage message = new NotificationMessage(
                "event-2", 42L, 7L, "COMMENT", 92L, 91L, "A work was commented on");
        EventOutbox row = row("NOTIFICATION", TOPIC, null, objectMapper.writeValueAsString(message), 1);
        dispatchable(row, 1);
        when(outboxService.errorMessage(any(Throwable.class)))
                .thenAnswer(invocation -> ((Throwable) invocation.getArgument(0)).getMessage());
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rocketMQTemplate).syncSend(eq(TOPIC), any(NotificationMessage.class));

        dispatcher.dispatch();

        verify(mapper).markFailure(eq(ROW_ID), eq(EventOutbox.PENDING), any(Date.class),
                eq("broker unavailable"));
        verify(outboxService, never()).logDead(any(EventOutbox.class), any(Throwable.class));
        verify(mapper, never()).markSent(ROW_ID);
    }

    @Test
    void movesExhaustedDeliveryToDeadLetter() throws Exception {
        NotificationMessage message = new NotificationMessage(
                "event-3", 42L, 7L, "COMMENT", 93L, 91L, "A work was commented on");
        EventOutbox row = row("NOTIFICATION", TOPIC, null, objectMapper.writeValueAsString(message), 8);
        dispatchable(row, 1);
        when(outboxService.errorMessage(any(Throwable.class)))
                .thenAnswer(invocation -> ((Throwable) invocation.getArgument(0)).getMessage());
        doThrow(new IllegalStateException("broker unavailable"))
                .when(rocketMQTemplate).syncSend(eq(TOPIC), any(NotificationMessage.class));

        dispatcher.dispatch();

        verify(mapper).markFailure(eq(ROW_ID), eq(EventOutbox.DEAD), any(Date.class),
                eq("broker unavailable"));
        verify(outboxService).logDead(eq(row), any(IllegalStateException.class));
        verify(mapper, never()).markSent(ROW_ID);
    }

    private void dispatchable(EventOutbox row, int claimResult) {
        when(mapper.findDispatchable(any(Date.class), eq(50))).thenReturn(List.of(row));
        when(mapper.claim(eq(ROW_ID), any(Date.class), any(Date.class))).thenReturn(claimResult);
    }

    private EventOutbox row(String eventType, String topic, String tag, String payload, int retryCount) {
        EventOutbox row = new EventOutbox();
        row.setId(ROW_ID);
        row.setEventId("event-" + ROW_ID);
        row.setEventType(eventType);
        row.setTopic(topic);
        row.setTag(tag);
        row.setPayload(payload);
        row.setRetryCount(retryCount);
        return row;
    }
}
