package org.hcz.notification.service;

import org.hcz.notification.entity.Notification;

public record NotificationSsePayload(Notification notification, long unreadCount) {
}
