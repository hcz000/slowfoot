package org.hcz.audit.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import org.hcz.work.entity.WorkAssetEntity;

import java.util.Date;
import java.util.List;


// 管理端作品审核列表项。
// 用独立 VO 而不是给 作品 加 @TableField(exist = false) 显示字段：作品 同时是
// /slowfoot/work/mine 的载荷，把管理端概念塞进去会变成面向终端用户的永久 null。

@Data
public class WorkAuditVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String workNo;
    private String title;
    private String description;
    private String content;
    private List<WorkAssetEntity> assets;
    private String workType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String userName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long typeId;
    private String typeName;
    private Long primaryFileId;
    private Long coverFileId;
    private Integer auditStatus;
    private String auditStateName;
    private String auditMsg;
    private Boolean openState;
    private Date gmtCreated;
}
