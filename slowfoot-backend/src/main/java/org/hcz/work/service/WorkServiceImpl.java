package org.hcz.work.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.common.vo.BasePage;
import org.hcz.work.dto.ImageWorkPublishRequest;
import org.hcz.work.dto.TextWorkPublishRequest;
import org.hcz.work.dto.VideoWorkPublishRequest;
import org.hcz.audit.dto.WorkAuditRequest;
import org.hcz.audit.dto.WorkAuditVO;
import org.hcz.work.dto.WorkRankItem;
import org.hcz.work.dto.WorkVO;
import org.hcz.work.entity.Work;
import org.hcz.work.entity.WorkShare;
import org.hcz.work.mapper.WorkMapper;
import org.hcz.feed.service.WorkFeedService;
import org.hcz.work.service.interaction.WorkInteractionService;
import org.hcz.work.service.moderation.WorkModerationService;
import org.hcz.work.service.publish.WorkPublishService;
import org.hcz.work.service.query.WorkQueryService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;


// 作品应用 facade：保留原有接口，业务实现按职责分布到独立服务。

@Service
public class WorkServiceImpl extends ServiceImpl<WorkMapper, Work> implements WorkService {

    private final WorkPublishService publishService;
    private final WorkQueryService queryService;
    private final WorkInteractionService interactionService;
    private final WorkModerationService moderationService;
    private final WorkFeedService feedService;

    public WorkServiceImpl(WorkPublishService publishService,
                           WorkQueryService queryService,
                           WorkInteractionService interactionService,
                           WorkModerationService moderationService,
                           WorkFeedService feedService) {
        this.publishService = publishService;
        this.queryService = queryService;
        this.interactionService = interactionService;
        this.moderationService = moderationService;
        this.feedService = feedService;
    }

    @Override
    public Work publishImage(ImageWorkPublishRequest request, Long userId) {
        return publishService.publishImage(request, userId);
    }

    @Override
    public Work publishText(TextWorkPublishRequest request, Long userId) {
        return publishService.publishText(request, userId);
    }

    @Override
    public Work publishVideoWork(VideoWorkPublishRequest request, Long userId) {
        return publishService.publishVideoWork(request, userId);
    }

    @Override
    public Work getMine(Long workId, Long userId) {
        return queryService.getMine(workId, userId);
    }

    @Override
    public IPage<Work> pageMine(BasePage basePage, Long userId) {
        return queryService.pageMine(basePage, userId);
    }

    @Override
    public void deleteMine(Long workId, Long userId) {
        moderationService.deleteMine(workId, userId);
    }

    @Override
    public void deleteForAdmin(Long workId) {
        moderationService.deleteForAdmin(workId);
    }

    @Override
    public IPage<WorkAuditVO> pageForAudit(BasePage basePage, Integer auditStatus, String workType,
                                           String workNo, String title) {
        return moderationService.pageForAudit(basePage, auditStatus, workType, workNo, title);
    }

    @Override
    public WorkAuditVO getAuditDetail(Long id) {
        return moderationService.getAuditDetail(id);
    }

    @Override
    public void audit(WorkAuditRequest request) {
        moderationService.audit(request);
    }

    @Override
    public WorkVO getPublicWork(Long id, Long userId) {
        return queryService.getPublicWork(id, userId);
    }

    @Override
    public Collection<WorkVO> pushWorks(Long userId) {
        return queryService.pushWorks(userId);
    }

    @Override
    public Collection<WorkVO> listByCategory(Long categoryId) {
        return queryService.listByCategory(categoryId);
    }

    @Override
    public IPage<WorkVO> searchWorks(String search, BasePage basePage, Long userId) {
        return queryService.searchWorks(search, basePage, userId);
    }

    @Override
    public boolean setLike(Long workId, boolean liked) {
        return interactionService.setLike(workId, liked);
    }

    @Override
    public boolean shareWork(WorkShare workShare) {
        return interactionService.shareWork(workShare);
    }

    @Override
    public void recordView(Long workId, Long userId) {
        interactionService.recordView(workId, userId);
    }

    @Override
    public boolean setFavorite(Long favoritesId, Long workId, boolean favorited) {
        return interactionService.setFavorite(favoritesId, workId, favorited);
    }

    @Override
    public LinkedHashMap<String, List<WorkVO>> getHistory(BasePage basePage) {
        return queryService.getHistory(basePage);
    }

    @Override
    public Collection<WorkVO> listByFavorites(Long favoritesId) {
        return queryService.listByFavorites(favoritesId);
    }

    @Override
    public List<WorkRankItem> hotRank() {
        return queryService.hotRank();
    }

    @Override
    public Collection<WorkVO> listSimilar(Long workId) {
        return queryService.listSimilar(workId);
    }

    @Override
    public IPage<WorkVO> listPublicByUserId(Long userId, BasePage basePage) {
        return queryService.listPublicByUserId(userId, basePage);
    }

    @Override
    public String getAuditQueueState() {
        return moderationService.getAuditQueueState();
    }

    @Override
    public List<Work> selectRecentWorks(long id, int days, int limit) {
        return queryService.selectRecentWorks(id, days, limit);
    }

    @Override
    public Collection<WorkVO> listHotWork() {
        return queryService.listHotWork();
    }

    @Override
    public Collection<WorkVO> followFeed(Long userId, Long lastTime) {
        return feedService.followFeed(userId, lastTime);
    }

    @Override
    public void initFollowFeed(Long userId) {
        feedService.initFollowFeed(userId);
    }

    @Override
    public void violations(Long id) {
        moderationService.violations(id);
    }
}
