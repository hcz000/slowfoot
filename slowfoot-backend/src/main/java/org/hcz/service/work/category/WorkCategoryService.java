package org.hcz.service.work.category;

import org.hcz.entity.work.WorkCategory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;



public interface WorkCategoryService extends IService<WorkCategory> {


// 获取分类下的标签
// @param typeId
// @return

    List<String> getLabels(Long typeId);


// 随机获取标签
// @return

    List<String> random10Labels();
}
