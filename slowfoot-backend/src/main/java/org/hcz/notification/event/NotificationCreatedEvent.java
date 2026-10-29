package org.hcz.notification.event;

import org.hcz.notification.entity.Notification;

public record NotificationCreatedEvent(Notification notification) {
}
