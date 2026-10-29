package org.hcz.audit.entity;


// 三张 *_audit_task 表共有的调度字段，供 AbstractAuditTaskService 读取。
// 用接口而非抽象父类，避免改动既有实体的继承链。

public interface AuditTaskEntity {

    Integer getStatus();

    Integer getRetryCount();
}
