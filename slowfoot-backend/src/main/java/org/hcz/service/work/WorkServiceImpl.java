package org.hcz.service.work;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.hcz.entity.vo.BasePage;
import org.hcz.entity.vo.ImageWorkPublishRequest;
import org.hcz.entity.vo.TextWorkPublishRequest;
import org.hcz.entity.vo.VideoWorkPublishRequest;
import org.hcz.entity.vo.WorkAuditRequest;
import org.hcz.entity.vo.WorkAuditVO;
import org.hcz.entity.vo.WorkRankItem;
import org.hcz.entity.vo.WorkVO;
import org.hcz.entity.work.Work;
import org.hcz.entity.work.WorkShare;
import org.hcz.mapper.work.WorkMapper;
import org.hcz.service.work.feed.WorkFeedService;
import org.hcz.service.work.interaction.WorkInteractionService;
import org.hcz.service.work.moderation.WorkModerationService;
import org.hcz.service.work.publish.WorkPublishService;
import org.hcz.service.work.query.WorkQueryService;
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
    public boolean toggleLike(Long workId) {
        return interactionService.toggleLike(workId);
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
    public boolean toggleFavorite(Long favoritesId, Long workId) {
        return interactionService.toggleFavorite(favoritesId, workId);
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
