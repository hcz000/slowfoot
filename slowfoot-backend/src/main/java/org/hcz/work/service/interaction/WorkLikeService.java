package org.hcz.work.service.interaction;

import org.hcz.work.entity.WorkLike;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;



public interface WorkLikeService extends IService<WorkLike> {


// 作品点赞，已点赞则取消
// @param workLike

    boolean setLike(Long workId, Long userId, boolean liked);


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
