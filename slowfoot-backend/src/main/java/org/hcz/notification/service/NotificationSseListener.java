package org.hcz.notification.service;

import org.hcz.notification.entity.Notification;
import org.hcz.notification.event.NotificationCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationSseListener {

    private final NotificationSseHub notificationSseHub;
    private final NotificationService notificationService;

    public NotificationSseListener(NotificationSseHub notificationSseHub,
                                    NotificationService notificationService) {
        this.notificationSseHub = notificationSseHub;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterNotificationCreated(NotificationCreatedEvent event) {
        Notification notification = event.notification();
        long unreadCount = notificationService.unreadCount(notification.getUserId());
        notificationSseHub.publish(notification.getUserId(), notification, unreadCount);
    }
}
