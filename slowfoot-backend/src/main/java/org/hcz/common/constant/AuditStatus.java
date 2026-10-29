package org.hcz.common.constant;


// 审核状态值，供作品和评论审核流程使用。

public enum AuditStatus {
    SUCCESS(0, "通过"),
    PROCESS(1, "审核中"),
    PASS(2, "失败"),
    MANUAL(3, "需要人工审核");

    private final int code;
    private final String name;

    AuditStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int code() {
        return code;
    }

    public static String getName(Integer code) {
        if (code == null) {
            return null;
        }
        for (AuditStatus status : values()) {
            if (status.code == code) {
                return status.name;
            }
        }
        return null;
    }
}
