package org.hcz.service.work.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.entity.work.WorkLike;
import org.hcz.mapper.work.WorkLikeMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.stream.Collectors;



@Service
public class WorkLikeServiceImpl extends ServiceImpl<WorkLikeMapper, WorkLike> implements WorkLikeService {


    @Override
    public boolean like(WorkLike workLike) {
        try {
            // 添加概率
            this.save(workLike);
        }catch (DuplicateKeyException e){
            // 存在则取消点赞
            this.remove(new LambdaQueryWrapper<WorkLike>().eq(WorkLike::getWorkId, workLike.getWorkId()).eq(WorkLike::getUserId, workLike.getUserId()));
            return false;
        }
        return true;
    }


    @Override
    public List<Long> getLikeUserIds(Long workId) {
        return this.list(new LambdaQueryWrapper<WorkLike>().eq(WorkLike::getWorkId, workId)).stream().map(WorkLike::getUserId).collect(Collectors.toList());
    }

    @Override
    public Boolean likeState(Long workId, Long userId) {

        if (userId == null) return  false;

        return this.count(new LambdaQueryWrapper<WorkLike>().eq(WorkLike::getWorkId, workId).eq(WorkLike::getUserId, userId)) == 1;
    }
}
