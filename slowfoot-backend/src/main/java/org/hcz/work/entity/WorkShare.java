package org.hcz.work.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;


// 作品分享记录，对应 work_share 表。
//

@Data
@EqualsAndHashCode(callSuper = false)
public class WorkShare extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long workId;

    private Long userId;

    private String ip;

}
