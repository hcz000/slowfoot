package org.hcz.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hcz.work.entity.Work;
import org.springframework.stereotype.Repository;

import java.util.List;


// 作品数据访问层。

@Repository
public interface WorkMapper extends BaseMapper<Work> {


// 只读取统一作品表中的视频作品。
//
// work_type = 'VIDEO' 是显式的产品过滤条件，不是迁移遗留：旧视频接口的载荷是视频形态的，
// 图片/文字作品不能从这里漏出去。裸 @Select 不经过 MyBatis-Plus 的逻辑删除注入，
// 因此显式带上 is_deleted = 0。

    @Select("SELECT * FROM work WHERE id = #{id} AND work_type = 'VIDEO' AND is_deleted = 0")
    Work selectVideoWorkById(@Param("id") Long id);


// 热度定时任务的分块扫描；work_type = 'VIDEO' 同上，是显式产品过滤条件。

    @Select("""
            SELECT id,share_count,view_count,like_count,favorite_count,gmt_created,title FROM work
            WHERE id > #{id} and work_type = 'VIDEO' and open_state = 0 and audit_status = 0
            and DATEDIFF(gmt_created,NOW())<=0 AND DATEDIFF(gmt_created,NOW())>- #{days} limit #{limit}""")
    List<Work> selectRecentWorks(@Param("id") long id, @Param("days") int days, @Param("limit") int limit);
}
