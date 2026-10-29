package org.hcz.work.mapper;

import org.hcz.work.entity.WorkLike;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;


public interface WorkLikeMapper extends BaseMapper<WorkLike> {

    @Insert("""
            INSERT IGNORE INTO work_like (work_id, user_id, is_deleted, gmt_created, gmt_updated)
            VALUES (#{workId}, #{userId}, 0, NOW(), NOW())""")
    int insertIfAbsent(@Param("workId") Long workId, @Param("userId") Long userId);

    @Update("""
            UPDATE work_like SET is_deleted = 0, gmt_updated = NOW()
            WHERE work_id = #{workId} AND user_id = #{userId} AND is_deleted = 1""")
    int restoreDeleted(@Param("workId") Long workId, @Param("userId") Long userId);

    @Delete("DELETE FROM work_like WHERE work_id = #{workId} AND user_id = #{userId} AND is_deleted = 0")
    int deleteActive(@Param("workId") Long workId, @Param("userId") Long userId);

}
