package org.hcz.constant;


// 值 persisted  该 notification 类型 column.

public enum NotificationType {
    LIKE("LIKE"),
    FAVORITE("FAVORITE"),
    FOLLOW("FOLLOW"),
    COMMENT("COMMENT"),
    REPLY("REPLY"),
    COMMENT_AUDIT("COMMENT_AUDIT"),
    WORK_AUDIT("WORK_AUDIT");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
