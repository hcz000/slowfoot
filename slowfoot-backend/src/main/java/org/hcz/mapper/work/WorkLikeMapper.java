package org.hcz.mapper.work;

import org.hcz.entity.work.WorkLike;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;


public interface WorkLikeMapper extends BaseMapper<WorkLike> {

    @Delete("DELETE FROM work_like WHERE work_id = #{workId} AND user_id = #{userId}")
    int hardDelete(@Param("workId") Long workId, @Param("userId") Long userId);
}
