package org.hcz.work.service.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hcz.work.entity.WorkLike;
import org.hcz.work.mapper.WorkLikeMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;



@Service
public class WorkLikeServiceImpl extends ServiceImpl<WorkLikeMapper, WorkLike> implements WorkLikeService {


    @Override
    public boolean setLike(Long workId, Long userId, boolean liked) {
        if (liked) {
            if (baseMapper.insertIfAbsent(workId, userId) == 1) {
                return true;
            }
            return baseMapper.restoreDeleted(workId, userId) == 1;
        }
        return baseMapper.deleteActive(workId, userId) == 1;
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
