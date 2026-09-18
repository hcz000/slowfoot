package org.hcz.entity.vo;

import lombok.Data;

import java.util.Date;


// 管理端作品审核列表项。
// 用独立 VO 而不是给 作品 加 @TableField(exist = false) 显示字段：作品 同时是
// /luckyjourney/work/mine 的载荷，把管理端概念塞进去会变成面向终端用户的永久 null。

@Data
public class WorkAuditVO {

    private Long id;
    private String workNo;
    private String title;
    private String workType;
    private Long userId;
    private String userName;
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
