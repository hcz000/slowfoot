package org.hcz.service.work.interaction;

import org.hcz.entity.work.WorkLike;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;



public interface WorkLikeService extends IService<WorkLike> {


// 作品点赞，已点赞则取消
// @param workLike

    boolean like(WorkLike workLike);


// 作品点赞用户
// @param workId
// @return

    List<Long> getLikeUserIds(Long workId);


// 点赞状态
// @param workId
// @param userId
// @return

    Boolean likeState(Long workId, Long userId);
}
