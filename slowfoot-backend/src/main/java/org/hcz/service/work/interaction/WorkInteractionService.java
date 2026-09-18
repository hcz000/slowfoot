package org.hcz.service.work.interaction;

import org.hcz.constant.NotificationType;
import org.hcz.constant.RedisKeys;
import org.hcz.entity.work.Work;
import org.hcz.entity.work.WorkLike;
import org.hcz.entity.work.WorkShare;
import org.hcz.event.work.WorkInteractionEvent;
import org.hcz.event.work.WorkInteractionType;
import org.hcz.exception.AppException;
import org.hcz.exception.ErrorCode;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.mapper.history.WorkViewHistoryMapper;
import org.hcz.service.notification.NotificationService;
import org.hcz.service.user.FavoritesService;
import org.hcz.service.work.query.WorkViewSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hcz.util.RedisCacheUtil;
import org.hcz.util.UserHolder;
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
    public boolean toggleLike(Long workId) {
        Work work = viewSupport.getWorkRecord(workId);
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        WorkLike workLike = new WorkLike();
        workLike.setWorkId(workId);
        workLike.setUserId(UserHolder.get());
        boolean result = workLikeService.like(workLike);
        if (result) notifySafely(work.getUserId(), UserHolder.get(), NotificationType.LIKE, workId, workId, "有人赞了你的视频");
        publishInteraction(work, WorkInteractionType.LIKE, result ? 1L : -1L, UserHolder.get());
        return result;
    }

    @Transactional
    public boolean toggleFavorite(Long favoritesId, Long workId) {
        Work work = viewSupport.getWorkRecord(workId);
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        boolean result = favoritesService.favorites(favoritesId, workId);
        if (result) notifySafely(work.getUserId(), UserHolder.get(), NotificationType.FAVORITE, workId, workId, "有人收藏了你的视频");
        publishInteraction(work, WorkInteractionType.FAVORITE, result ? 1L : -1L, UserHolder.get());
        return result;
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

    private void notifySafely(Long recipientId, Long actorId, NotificationType type,
                              Long targetId, Long workId, String content) {
        try {
            notificationService.publish(recipientId, actorId, type, targetId, workId, content);
        } catch (Exception ignored) {
            // 通知的持久化不能改变用户的主操作。
        }
    }
}
