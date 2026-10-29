package org.hcz.notification.service;

import org.hcz.notification.entity.Notification;
import org.hcz.notification.event.NotificationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSseListenerTests {

    private static final long USER_ID = 42L;

    @Mock
    private NotificationSseHub hub;

    @Mock
    private NotificationService notificationService;

    @Test
    void sendsCreatedNotificationWithTheCurrentUnreadCount() {
        Notification notification = new Notification();
        notification.setUserId(USER_ID);
        notification.setContent("A work was liked");
        when(notificationService.unreadCount(USER_ID)).thenReturn(3L);
        NotificationSseListener listener = new NotificationSseListener(hub, notificationService);

        listener.afterNotificationCreated(new NotificationCreatedEvent(notification));

        InOrder order = inOrder(notificationService, hub);
        order.verify(notificationService).unreadCount(USER_ID);
        order.verify(hub).publish(USER_ID, notification, 3L);
    }
}