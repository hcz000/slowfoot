package org.hcz.service.notification;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.constant.NotificationType;
import org.hcz.entity.notification.Notification;
import org.hcz.entity.vo.BasePage;
import org.hcz.event.notification.NotificationEvent;

public interface NotificationService {

    void publish(Long userId, Long actorId, NotificationType type,
                 Long targetId, Long workId, String content);

    void persist(NotificationEvent event);

    IPage<Notification> page(Long userId, BasePage basePage);

    long unreadCount(Long userId);

    void markRead(Long id, Long userId);

    int markAllRead(Long userId);
}
