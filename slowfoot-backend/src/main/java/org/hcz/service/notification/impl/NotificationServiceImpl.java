package org.hcz.service.notification.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.constant.NotificationType;
import org.hcz.entity.notification.Notification;
import org.hcz.entity.user.User;
import org.hcz.entity.vo.BasePage;
import org.hcz.messaging.notification.NotificationEvent;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.mapper.notification.NotificationMapper;
import org.hcz.mapper.user.UserMapper;
import org.hcz.service.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Long userId, Long actorId, NotificationType type,
                        Long targetId, Long workId, String content) {
        if (userId == null || actorId != null && actorId.equals(userId)) {
            return;
        }
        applicationEventPublisher.publishEvent(new NotificationEvent(this, userId, actorId,
                type == null ? null : type.value(),
                targetId, workId, StringUtils.hasText(content) ? content : "你有一条新的通知"));
    }

    @Override
    public void persist(NotificationEvent event) {
        Notification notification = new Notification();
        notification.setEventId(event.getEventId());
        notification.setUserId(event.getUserId());
        notification.setActorId(event.getActorId());
        notification.setType(event.getType());
        notification.setTargetId(event.getTargetId());
        notification.setWorkId(event.getWorkId());
        notification.setContent(event.getContent());
        notification.setReadState(false);
        try {
            save(notification);
        } catch (DuplicateKeyException ignored) {
            // 重试的事件不能产生重复的通知。
        }
    }

    @Override
    public IPage<Notification> page(Long userId, BasePage basePage) {
        if (userId == null) {
            return basePage.page();
        }
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getGmtCreated);
        IPage<Notification> result = page(basePage.page(), wrapper);
        Map<Long, User> actors = result.getRecords().stream()
                .map(Notification::getActorId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), ids -> userMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()))));
        result.getRecords().forEach(notification -> {
            User actor = actors.get(notification.getActorId());
            if (actor != null) {
                notification.setActorName(actor.getNickName());
                notification.setActorAvatar(actor.getAvatar());
            }
        });
        return result;
    }

    @Override
    public long unreadCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        return count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadState, false));
    }

    @Override
    public void markRead(Long id, Long userId) {
        UpdateWrapper<Notification> wrapper = new UpdateWrapper<>();
        wrapper.set("read_state", true)
                .eq("id", id)
                .eq("user_id", userId)
                .eq("read_state", false);
        if (!update(null, wrapper)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在或已经读过");
        }
    }

    @Override
    public int markAllRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        LambdaQueryWrapper<Notification> countWrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadState, false);
        int count = (int) count(countWrapper);
        if (count > 0) {
            UpdateWrapper<Notification> updateWrapper = new UpdateWrapper<>();
            updateWrapper.set("read_state", true)
                    .eq("user_id", userId)
                    .eq("read_state", false);
            update(null, updateWrapper);
        }
        return count;
    }
}
