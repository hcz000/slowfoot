package org.hcz.work.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;


// 统一作品服务。

public interface WorkService extends IService<Work> {

    Work publishImage(ImageWorkPublishRequest request, Long userId);

    Work publishText(TextWorkPublishRequest request, Long userId);


// 发布/修改视频作品。
// 修改无法更换视频源。

    Work publishVideoWork(VideoWorkPublishRequest request, Long userId);

    Work getMine(Long workId, Long userId);

    IPage<Work> pageMine(BasePage basePage, Long userId);

    void deleteMine(Long workId, Long userId);


// 管理端删除：不校验属主。

    void deleteForAdmin(Long workId);


// 管理端审核队列，auditStatus 传 3 即待人工复审。

    IPage<WorkAuditVO> pageForAudit(BasePage basePage, Integer auditStatus, String workType,
                                    String workNo, String title);

    WorkAuditVO getAuditDetail(Long id);


// 管理端人工复审。

    void audit(WorkAuditRequest request);


// 获取公开的作品详情
// @param id
// @param userId 当前用户id

    WorkVO getPublicWork(Long id, Long userId);


// 主页推送作品

    Collection<WorkVO> pushWorks(Long userId);


// 根据作品分类获取作品,乱序
// @param categoryId

    Collection<WorkVO> listByCategory(Long categoryId);


// 搜索作品

    IPage<WorkVO> searchWorks(String search, BasePage basePage, Long userId);


    boolean setLike(Long workId, boolean liked);


// 分享作品

    boolean shareWork(WorkShare workShare);


// 添加浏览记录

    void recordView(Long workId, Long userId);


    boolean setFavorite(Long favoritesId, Long workId, boolean favorited);


// 获取当前用户浏览记录,带分页

    LinkedHashMap<String, List<WorkVO>> getHistory(BasePage basePage);


// 根据收藏夹获取作品

    Collection<WorkVO> listByFavorites(Long favoritesId);


// 获取热度排行榜

    List<WorkRankItem> hotRank();


// 根据标签推送相似作品

    Collection<WorkVO> listSimilar(Long workId);


// 根据userId获取对应作品,只包含公开的

    IPage<WorkVO> listPublicByUserId(Long userId, BasePage basePage);


// 获取当前审核队列

    String getAuditQueueState();


// 分块扫描 N 天内的视频作品，供热度统计使用
// @param id id
// @param days 天数
// @param limit 限制

    List<Work> selectRecentWorks(long id, int days, int limit);


// 获取热门作品

    Collection<WorkVO> listHotWork();


// 关注流
// @param userId 用户id
// @param lastTime 滚动分页参数，首次为null，后续为上次的末尾作品时间

    Collection<WorkVO> followFeed(Long userId, Long lastTime);


// 拉模式

    void initFollowFeed(Long userId);


// 下架作品

    void violations(Long id);
}
