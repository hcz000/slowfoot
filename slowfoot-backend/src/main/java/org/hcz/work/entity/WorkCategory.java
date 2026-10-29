package org.hcz.work.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;


// 作品分类（频道/分区），对应 type 表 —— 表名未随实体改名，故显式声明 @TableName。
//

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("type")
public class WorkCategory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "分类名称不可为空")
    private String name;

    private String description;

    private String icon;

    private Boolean open;

    private String labelNames;

    private Integer sort;

    @TableField(exist = false)
    private Boolean used;

    public List<String> buildLabel(){
        return Arrays.asList(labelNames.split(","));
    }
}
