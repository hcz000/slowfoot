package org.hcz.common.constant;


// 审核任务状态值，由审核任务记录存储。

public enum AuditTaskStatus {
    PENDING(0),
    PROCESSING(1),
    SUCCESS(2),
    FAILED(3);

    private final int code;

    AuditTaskStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
