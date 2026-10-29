package org.hcz.work.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.hcz.work.entity.TextWork;
import org.springframework.stereotype.Repository;


// Data access 用于 text-specific 作品 字段.

@Repository
public interface TextWorkMapper extends BaseMapper<TextWork> {
}
