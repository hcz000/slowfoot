package org.hcz.entity.work;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.entity.BaseEntity;


// 作品资源实体，对应 work_asset 表。

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_asset")
public class WorkAssetEntity extends BaseEntity {

    private Long workId;
    private Long fileId;
    private String assetType;
    private Integer sortOrder;


// 返回给前端的临时访问地址，不落库。

    @TableField(exist = false)
    private String url;
}
