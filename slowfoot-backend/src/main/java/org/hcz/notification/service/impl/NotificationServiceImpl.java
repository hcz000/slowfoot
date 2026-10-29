package org.hcz.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.common.constant.NotificationType;
import org.hcz.notification.entity.Notification;
import org.hcz.user.entity.User;
import org.hcz.common.vo.BasePage;
import org.hcz.notification.event.NotificationEvent;
import org.hcz.notification.event.NotificationCreatedEvent;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.notification.mapper.NotificationMapper;
import org.hcz.user.mapper.UserMapper;
import org.hcz.common.messaging.service.EventConsumeService;
import org.hcz.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private EventConsumeService consumeService;

    @Override
    public void publish(Long userId, Long actorId, NotificationType type,
                        Long targetId, Long workId, String content) {
        if (userId == null || actorId != null && actorId.equals(userId)) {
            return;
        }
        applicationEventPublisher.publishEvent(new NotificationEvent(this, userId, actorId,
                type == null ? null : type.value(),
                targetId, workId, StringUtils.hasText(content) ? content : "You have a new notification"));
    }

    @Override
    @Transactional
    public void persist(NotificationEvent event) {
        if (!consumeService.tryClaim(event.getEventId(), "notification")) {
            return;
        }
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
            if (save(notification)) {
                applicationEventPublisher.publishEvent(new NotificationCreatedEvent(notification));
            }
        } catch (DuplicateKeyException ignored) {
            // Existing unique key is also a valid idempotent outcome.
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
        List<Long> actorIds = result.getRecords().stream()
                .map(Notification::getActorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> actors = actorIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(actorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        result.getRecords().forEach(notification -> {
            Long actorId = notification.getActorId();
            User actor = actorId == null ? null : actors.get(actorId);
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
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Notification not found or already read");
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
