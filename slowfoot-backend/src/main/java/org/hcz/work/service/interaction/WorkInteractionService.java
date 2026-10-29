package org.hcz.work.service.interaction;

import org.hcz.common.constant.NotificationType;
import org.hcz.common.constant.RedisKeys;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkLike;
import org.hcz.work.entity.WorkShare;
import org.hcz.work.event.WorkInteractionEvent;
import org.hcz.work.event.WorkInteractionType;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.history.mapper.WorkViewHistoryMapper;
import org.hcz.notification.service.NotificationService;
import org.hcz.user.service.FavoritesService;
import org.hcz.work.service.query.WorkViewSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hcz.common.util.RedisCacheUtil;
import org.hcz.common.util.UserHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class WorkInteractionService {

    private static final Logger log = LoggerFactory.getLogger(WorkInteractionService.class);

    private final WorkMapper workMapper;
    private final WorkLikeService workLikeService;
    private final WorkShareService workShareService;
    private final FavoritesService favoritesService;
    private final NotificationService notificationService;
    private final RedisCacheUtil redisCacheUtil;
    private final WorkViewHistoryMapper workViewHistoryMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WorkViewSupport viewSupport;

    public WorkInteractionService(WorkMapper workMapper,
                                     WorkLikeService workLikeService,
                                     WorkShareService workShareService,
                                     FavoritesService favoritesService,
                                     NotificationService notificationService,
                                     RedisCacheUtil redisCacheUtil,
                                     WorkViewHistoryMapper workViewHistoryMapper,
                                     ApplicationEventPublisher applicationEventPublisher,
                                     WorkViewSupport viewSupport) {
        this.workMapper = workMapper;
        this.workLikeService = workLikeService;
        this.workShareService = workShareService;
        this.favoritesService = favoritesService;
        this.notificationService = notificationService;
        this.redisCacheUtil = redisCacheUtil;
        this.workViewHistoryMapper = workViewHistoryMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.viewSupport = viewSupport;
    }

    @Transactional
    public boolean setLike(Long workId, boolean liked) {
        Long userId = UserHolder.get();
        Work work = viewSupport.getWorkRecord(workId);
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        boolean changed = workLikeService.setLike(workId, userId, liked);
        if (changed) {
            if (liked) {
                publishNotification(work.getUserId(), userId, NotificationType.LIKE,
                        workId, workId, "有人赞了你的作品");
            }
            publishInteraction(work, WorkInteractionType.LIKE, liked ? 1L : -1L, userId);
        }
        return changed;
    }

    @Transactional
    public boolean setFavorite(Long favoritesId, Long workId, boolean favorited) {
        Long userId = UserHolder.get();
        Work work = viewSupport.getWorkRecord(workId);
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        boolean changed = favoritesService.setFavorite(favoritesId, workId, userId, favorited);
        if (changed) {
            if (favorited) {
                publishNotification(work.getUserId(), userId, NotificationType.FAVORITE,
                        workId, workId, "有人收藏了你的作品");
            }
            publishInteraction(work, WorkInteractionType.FAVORITE, favorited ? 1L : -1L, userId);
        }
        return changed;
    }

    @Transactional
    public boolean shareWork(WorkShare workShare) {
        Work work = viewSupport.getWorkRecord(workShare.getWorkId());
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        boolean result = workShareService.share(workShare);
        if (result) publishInteraction(work, WorkInteractionType.SHARE, 1L, workShare.getUserId());
        return result;
    }

    @Transactional
    public void recordView(Long workId, Long userId) {
        if (workId == null || userId == null
                || workViewHistoryMapper.markViewed(userId, workId, new Date()) <= 0) {
            return;
        }
        Work work = viewSupport.getWorkRecord(workId);
        if (work == null) return;
        try {
            String key = RedisKeys.HISTORY_WORK + workId + ":" + userId;
            redisCacheUtil.setIfAbsent(key, workId, RedisKeys.HISTORY_TIME);
            redisCacheUtil.zadd(RedisKeys.USER_HISTORY_WORK + userId,
                    new Date().getTime(), workId, RedisKeys.HISTORY_TIME);
        } catch (Exception e) {
            log.warn("Browse history cache update failed, userId={}, workId={}", userId, workId, e);
        }
        publishInteraction(work, WorkInteractionType.HISTORY, 1L, userId);
    }

    private void publishInteraction(Work work, WorkInteractionType type, long countDelta, Long userId) {
        applicationEventPublisher.publishEvent(new WorkInteractionEvent(
                this, work.getId(), work.getUserId(), work.getTitle(), work.buildLabel(), userId, type, countDelta));
    }

    private void publishNotification(Long recipientId, Long actorId, NotificationType type,
                                     Long targetId, Long workId, String content) {
        // The event is persisted to the Outbox in this transaction, so a
        // persistence failure must roll back the interaction as well.
        notificationService.publish(recipientId, actorId, type, targetId, workId, content);
    }
}
