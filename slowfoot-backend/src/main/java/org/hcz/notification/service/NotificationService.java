package org.hcz.notification.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.common.constant.NotificationType;
import org.hcz.notification.entity.Notification;
import org.hcz.common.vo.BasePage;
import org.hcz.notification.event.NotificationEvent;

public interface NotificationService {

    void publish(Long userId, Long actorId, NotificationType type,
                 Long targetId, Long workId, String content);

    void persist(NotificationEvent event);

    IPage<Notification> page(Long userId, BasePage basePage);

    long unreadCount(Long userId);

    void markRead(Long id, Long userId);

    int markAllRead(Long userId);
}
