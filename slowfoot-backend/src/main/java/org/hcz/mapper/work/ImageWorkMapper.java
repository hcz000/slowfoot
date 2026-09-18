package org.hcz.mapper.work;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.hcz.entity.work.ImageWork;
import org.springframework.stereotype.Repository;


// Data access 用于 image-specific 作品 records.

@Repository
public interface ImageWorkMapper extends BaseMapper<ImageWork> {
}
