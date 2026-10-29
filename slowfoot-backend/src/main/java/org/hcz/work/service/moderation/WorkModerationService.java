package org.hcz.work.service.moderation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.hcz.common.constant.AuditStatus;
import org.hcz.common.constant.RedisKeys;
import org.hcz.user.entity.User;
import org.hcz.common.vo.BasePage;
import org.hcz.audit.dto.WorkAuditRequest;
import org.hcz.audit.dto.WorkAuditVO;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkAssetEntity;
import org.hcz.work.entity.WorkCategory;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.hcz.work.mapper.ImageWorkMapper;
import org.hcz.work.mapper.TextWorkMapper;
import org.hcz.work.mapper.VideoWorkMapper;
import org.hcz.work.mapper.WorkAssetMapper;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.audit.service.WorkPublishAuditService;
import org.hcz.feed.service.FeedService;
import org.hcz.recommendation.service.InterestPushService;
import org.hcz.user.service.FollowService;
import org.hcz.user.service.UserService;
import org.hcz.work.service.category.WorkCategoryService;
import org.hcz.work.service.interaction.HotRankCache;
import org.hcz.work.service.interaction.WorkInteractionCleanupService;
import org.hcz.work.service.query.WorkViewSupport;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WorkModerationService {

    private final WorkMapper workMapper;
    private final WorkAssetMapper workAssetMapper;
    private final ImageWorkMapper imageWorkMapper;
    private final TextWorkMapper textWorkMapper;
    private final VideoWorkMapper videoWorkMapper;
    private final WorkCategoryService workCategoryService;
    private final UserService userService;
    private final WorkPublishAuditService workPublishAuditService;
    private final InterestPushService interestPushService;
    private final FollowService followService;
    private final FeedService feedService;
    private final RedisTemplate redisTemplate;
    private final HotRankCache hotRankCache;
    private final WorkInteractionCleanupService cleanupService;
    private final WorkViewSupport viewSupport;

    public WorkModerationService(WorkMapper workMapper,
                                    WorkAssetMapper workAssetMapper,
                                    ImageWorkMapper imageWorkMapper,
                                    TextWorkMapper textWorkMapper,
                                    VideoWorkMapper videoWorkMapper,
                                    WorkCategoryService workCategoryService,
                                    UserService userService,
                                    WorkPublishAuditService workPublishAuditService,
                                    InterestPushService interestPushService,
                                    FollowService followService,
                                    FeedService feedService,
                                    RedisTemplate redisTemplate,
                                    HotRankCache hotRankCache,
                                    WorkInteractionCleanupService cleanupService,
                                    WorkViewSupport viewSupport) {
        this.workMapper = workMapper;
        this.workAssetMapper = workAssetMapper;
        this.imageWorkMapper = imageWorkMapper;
        this.textWorkMapper = textWorkMapper;
        this.videoWorkMapper = videoWorkMapper;
        this.workCategoryService = workCategoryService;
        this.userService = userService;
        this.workPublishAuditService = workPublishAuditService;
        this.interestPushService = interestPushService;
        this.followService = followService;
        this.feedService = feedService;
        this.redisTemplate = redisTemplate;
        this.hotRankCache = hotRankCache;
        this.cleanupService = cleanupService;
        this.viewSupport = viewSupport;
    }

    @Transactional
    public void deleteMine(Long workId, Long userId) {
        Work work = workMapper.selectById(workId);
        if (work == null) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "作品不存在");
        if (!userId.equals(work.getUserId())) throw new AppException(ErrorCode.FORBIDDEN, "无权删除该作品");
        doDelete(work);
    }

    @Transactional
    public void deleteForAdmin(Long workId) {
        Work work = workMapper.selectById(workId);
        if (work == null) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "作品不存在");
        doDelete(work);
    }

    private void doDelete(Work work) {
        Long workId = work.getId();
        workAssetMapper.delete(new LambdaQueryWrapper<WorkAssetEntity>().eq(WorkAssetEntity::getWorkId, workId));
        imageWorkMapper.deleteById(workId);
        textWorkMapper.deleteById(workId);
        videoWorkMapper.deleteById(workId);
        workMapper.deleteById(workId);
        cleanupService.cleanup(workId, work.getUserId(), work);
    }

    public IPage<WorkAuditVO> pageForAudit(BasePage basePage, Integer auditStatus, String workType,
                                           String workNo, String title) {
         LambdaQueryWrapper<Work> wrapper = new LambdaQueryWrapper<Work>()
                 .in(Work::getAuditStatus,
                         List.of(AuditStatus.PROCESS.code(), AuditStatus.MANUAL.code()))
                 .eq(auditStatus != null, Work::getAuditStatus, auditStatus)
                 .eq(StringUtils.hasText(workType), Work::getWorkType, workType)
                 .like(StringUtils.hasText(workNo), Work::getWorkNo, workNo)
                 .like(StringUtils.hasText(title), Work::getTitle, title)
                .orderByAsc(Work::getAuditStatus)
                .orderByDesc(Work::getGmtCreated);
        IPage<Work> page = workMapper.selectPage(basePage.page(), wrapper);
        List<Work> records = page.getRecords();
        Map<Long, String> userNames = records.isEmpty() ? Map.of() : userService.list(new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getNickName)
                        .in(User::getId, records.stream().map(Work::getUserId).distinct().toList()))
                .stream().collect(Collectors.toMap(User::getId, User::getNickName));
        List<Long> typeIds = records.stream().map(Work::getTypeId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> typeNames = typeIds.isEmpty() ? Map.of() : workCategoryService.listByIds(typeIds).stream()
                .collect(Collectors.toMap(WorkCategory::getId, WorkCategory::getName));
        return page.convert(work -> toAuditVO(work, userNames, typeNames));
    }

    public WorkAuditVO getAuditDetail(Long id) {
        Work work = workMapper.selectById(id);
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        viewSupport.loadDetails(work);
        User user = userService.getById(work.getUserId());
        WorkCategory category = workCategoryService.getById(work.getTypeId());
        return toAuditVO(work,
                user == null ? Map.of() : Map.of(work.getUserId(), user.getNickName()),
                category == null ? Map.of() : Map.of(work.getTypeId(), category.getName()));
    }

    @Transactional
    public void audit(WorkAuditRequest request) {
        if (!Objects.equals(request.getAuditStatus(), AuditStatus.SUCCESS.code())
                && !Objects.equals(request.getAuditStatus(), AuditStatus.PASS.code())) {
            throw new AppException(ErrorCode.INVALID_AUDIT_STATUS);
        }
        Work work = workMapper.selectById(request.getId());
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        Integer oldStatus = work.getAuditStatus();
        if (!Objects.equals(oldStatus, AuditStatus.PROCESS.code())
                && !Objects.equals(oldStatus, AuditStatus.MANUAL.code())) {
            throw new AppException(ErrorCode.INVALID_AUDIT_STATUS);
        }
        work.setAuditStatus(request.getAuditStatus());
        work.setAuditMsg(StringUtils.hasText(request.getAuditMsg()) ? request.getAuditMsg()
                : (Objects.equals(request.getAuditStatus(), AuditStatus.SUCCESS.code()) ? "通过" : "人工审核未通过"));
        workMapper.updateById(work);
        workPublishAuditService.publishAuditResult(work, oldStatus);
    }

    public String getAuditQueueState() {
        return workPublishAuditService.getAuditQueueState() ? "快速" : "慢速";
    }

    public void violations(Long id) {
        Work work = viewSupport.getWorkRecord(id);
        if (work == null) throw new AppException(ErrorCode.WORK_NOT_FOUND);
        WorkCategory category = workCategoryService.getById(work.getTypeId());
        work.setLabelNames(category.getLabelNames());
        work.setOpenState(true);
        work.setAuditMsg("该作品违反了慢脚平台的规则,已被下架私密");
        work.setAuditStatus(AuditStatus.PASS.code());
        interestPushService.deleteSystemTypeStockIn(work);
        interestPushService.deleteSystemStockIn(work);
        Long userId = work.getUserId();
        redisTemplate.opsForZSet().remove(RedisKeys.OUT_FOLLOW + userId, id);
        Collection<Long> fansIds = followService.getFans(userId, null);
        feedService.deleteInBoxFeed(userId, Collections.singletonList(id));
        feedService.deleteOutBoxFeed(userId, fansIds, id);
        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DATE);
        redisTemplate.opsForSet().remove(RedisKeys.HOT_WORK + today, id);
        redisTemplate.opsForSet().remove(RedisKeys.HOT_WORK + (today - 1), id);
        redisTemplate.opsForSet().remove(RedisKeys.HOT_WORK + (today - 2), id);
        hotRankCache.removeWork(id);
        workMapper.updateById(work);
    }

    private WorkAuditVO toAuditVO(Work work, Map<Long, String> userNames, Map<Long, String> typeNames) {
        WorkAuditVO vo = new WorkAuditVO();
        vo.setId(work.getId());
        vo.setWorkNo(work.getWorkNo());
        vo.setTitle(work.getTitle());
        vo.setDescription(work.getDescription());
        vo.setContent(work.getContent());
        vo.setAssets(work.getAssets());
        vo.setWorkType(work.getWorkType());
        vo.setUserId(work.getUserId());
        vo.setUserName(userNames.get(work.getUserId()));
        vo.setTypeId(work.getTypeId());
        vo.setTypeName(typeNames.get(work.getTypeId()));
        vo.setPrimaryFileId(work.getPrimaryFileId());
        vo.setCoverFileId(work.getCoverFileId());
        vo.setAuditStatus(work.getAuditStatus());
        vo.setAuditStateName(AuditStatus.getName(work.getAuditStatus()));
        vo.setAuditMsg(work.getAuditMsg());
        vo.setOpenState(work.getOpenState());
        vo.setGmtCreated(work.getGmtCreated());
        return vo;
    }
}
