package org.hcz.mapper.work;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.hcz.entity.work.VideoWork;
import org.springframework.stereotype.Repository;


// Data access 用于 video-specific 作品 字段.

@Repository
public interface VideoWorkMapper extends BaseMapper<VideoWork> {
}
