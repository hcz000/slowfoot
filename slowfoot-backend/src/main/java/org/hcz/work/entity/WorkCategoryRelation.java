package org.hcz.work.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;


// 作品与分类的关联，对应 work_category_relation 表。
//

@Data
@EqualsAndHashCode(callSuper = false)
public class WorkCategoryRelation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long workId;

    private Long typeId;

}
