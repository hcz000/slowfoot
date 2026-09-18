package org.hcz.mapper.history;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hcz.entity.history.WorkViewHistory;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface WorkViewHistoryMapper extends BaseMapper<WorkViewHistory> {

    @Insert("INSERT INTO work_view_history(user_id, work_id, viewed_at) VALUES(#{userId}, #{workId}, #{viewedAt}) "
            + "ON DUPLICATE KEY UPDATE viewed_at = IF(viewed_at < DATE_SUB(NOW(), INTERVAL 5 DAY), VALUES(viewed_at), viewed_at)")
    int markViewed(@Param("userId") Long userId,
                   @Param("workId") Long workId,
                   @Param("viewedAt") Date viewedAt);

    @Select("SELECT id, user_id, work_id, viewed_at FROM work_view_history "
            + "WHERE user_id = #{userId} ORDER BY viewed_at DESC LIMIT #{offset}, #{limit}")
    List<WorkViewHistory> listRecent(@Param("userId") Long userId,
                                     @Param("offset") long offset,
                                     @Param("limit") long limit);

    @Delete("DELETE FROM work_view_history WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
