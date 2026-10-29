package org.hcz.notification.service;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.hcz.common.constant.NotificationType;
import org.hcz.notification.entity.Notification;
import org.hcz.notification.event.NotificationCreatedEvent;
import org.hcz.notification.event.NotificationEvent;
import org.hcz.notification.mapper.NotificationMapper;
import org.hcz.user.mapper.UserMapper;
import org.hcz.common.messaging.service.EventConsumeService;
import org.hcz.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTests {

    private static final long USER_ID = 42L;
    private static final long ACTOR_ID = 7L;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EventConsumeService consumeService;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", (BaseMapper<Notification>) notificationMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "applicationEventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "consumeService", consumeService);
    }

    @Test
    void publishIgnoresMissingRecipientsAndSelfNotifications() {
        service.publish(null, ACTOR_ID, NotificationType.LIKE, 91L, 91L, "like");
        service.publish(USER_ID, USER_ID, NotificationType.LIKE, 91L, 91L, "like");

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publishCreatesAnEventWithFallbackTextForBlankContent() {
        service.publish(USER_ID, ACTOR_ID, NotificationType.LIKE, 91L, 90L, "  ");

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        NotificationEvent notificationEvent = event.getValue();
        assertEquals(USER_ID, notificationEvent.getUserId());
        assertEquals(ACTOR_ID, notificationEvent.getActorId());
        assertEquals(NotificationType.LIKE.value(), notificationEvent.getType());
        assertEquals(91L, notificationEvent.getTargetId());
        assertEquals(90L, notificationEvent.getWorkId());
        assertEquals("You have a new notification", notificationEvent.getContent());
    }

    @Test
    void persistSavesAnUnreadNotificationAndPublishesCreatedEvent() {
        NotificationEvent event = event("event-1");
        when(consumeService.tryClaim("event-1", "notification")).thenReturn(true);
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        service.persist(event);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(saved.capture());
        assertEquals("event-1", saved.getValue().getEventId());
        assertEquals(USER_ID, saved.getValue().getUserId());
        assertEquals(ACTOR_ID, saved.getValue().getActorId());
        assertEquals("LIKE", saved.getValue().getType());
        assertEquals(91L, saved.getValue().getTargetId());
        assertEquals(90L, saved.getValue().getWorkId());
        assertEquals("A work was liked", saved.getValue().getContent());
        assertFalse(saved.getValue().getReadState());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());
        NotificationCreatedEvent createdEvent = assertInstanceOf(NotificationCreatedEvent.class,
                published.getValue());
        assertEquals(saved.getValue(), createdEvent.notification());
    }

    @Test
    void persistSkipsEventsAlreadyClaimedByAnotherConsumer() {
        when(consumeService.tryClaim("event-1", "notification")).thenReturn(false);

        service.persist(event("event-1"));

        verify(notificationMapper, never()).insert(any(Notification.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void persistTreatsDuplicateEventRowsAsAnIdempotentOutcome() {
        when(consumeService.tryClaim("event-1", "notification")).thenReturn(true);
        when(notificationMapper.insert(any(Notification.class)))
                .thenThrow(new DuplicateKeyException("duplicate event id"));

        assertDoesNotThrow(() -> service.persist(event("event-1")));

        verifyNoInteractions(eventPublisher);
    }

    private NotificationEvent event(String eventId) {
        return new NotificationEvent(this, eventId, USER_ID, ACTOR_ID, "LIKE",
                91L, 90L, "A work was liked");
    }
}
