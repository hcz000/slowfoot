package org.hcz.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;

import java.io.Serializable;
import java.util.Date;


// 作品审核发件箱任务。
//
// 快照字段只在视频作品上有意义：视频作品支持编辑，审核时需要知道「改了什么」，
// 而审核是异步的，实读的那一行可能已经被后续编辑覆盖。图片/文字作品没有编辑接口，
// 快照留空即可。

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("work_audit_task")
public class WorkAuditTask extends BaseEntity implements AuditTaskEntity, Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private Long workId;

// 是否新增作品（0 修改，1 新增）；图片/文字作品恒为 true。

    private Boolean isAdd;

// 提交审核时的开放状态快照，仅视频作品使用。

    private Boolean oldState;

// 提交审核时的目标开放状态快照，仅视频作品使用。

    private Boolean newState;

// 修改前的标题快照，仅视频作品使用。

    private String oldTitle;

// 修改前的简介快照，仅视频作品使用。

    private String oldDescription;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryAt;
    private Date lockedUntil;
    private String lastError;
}
