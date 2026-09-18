package org.hcz.event.mq;

public final class RocketMQTopics {

    public static final String WORK_INTERACTION = "${rocketmq.topics.work-interaction}";
    public static final String NOTIFICATION = "${rocketmq.topics.notification}";
    public static final String AUDIT_SUBMITTED = "${rocketmq.topics.audit-submitted}";
    public static final String AUDIT_COMPLETED = "${rocketmq.topics.audit-completed}";

    public static final String INTERACTION_TAG = "INTERACTION";
    public static final String WORK_AUDIT_TAG = "WORK_AUDIT";
    public static final String COMMENT_AUDIT_TAG = "COMMENT_AUDIT";

    private RocketMQTopics() {
    }
}
