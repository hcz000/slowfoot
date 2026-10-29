package org.hcz.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hcz.common.entity.BaseEntity;

import java.io.Serializable;
import java.util.Date;


// 视频异步审核任务：提交阿里云后记录 taskId 与第一阶段（封面/标题/简介）已收集的失败项，
// 由 VideoAuditResultPoller 轮询结论并推进到终态（completed / failed）。
// 状态机独立于 work_audit_task（派发链路），避免两种生命周期互相踩。

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("video_audit_job")
public class VideoAuditJob extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long workId;
    private Long auditTaskId;
    private String eventId;

// 视频文件 ID。重提交时经 FileService + AliyunOssFileService 重新生成访问 URL，
// 不持久化带签名的临时地址。
    private Long fileId;
    private String aliyunTaskId;

// 第一阶段同步项的失败消息（换行连接，已收敛到列宽），终局时与视频结论合并。
    private String pendingFailures;

// 提交审核时的目标开放状态快照，沿用 AuditSummary.ofVideo 语义。
    private Boolean newState;

    private Integer status;
    private Integer retryCount;
    private Date nextPollAt;
    private Date lockedUntil;
    private String lastError;
}
