package org.hcz.work.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;


// 作品点赞记录，对应 work_like 表。
//

@Data
@EqualsAndHashCode(callSuper = false)
public class WorkLike extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long workId;

    private Long userId;

}
