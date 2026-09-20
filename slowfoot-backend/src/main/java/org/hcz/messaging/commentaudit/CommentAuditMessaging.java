package org.hcz.messaging.commentaudit;

final class CommentAuditMessaging {

    static final String SUBMITTED_TOPIC = "${rocketmq.topics.audit-submitted}";
    static final String COMMENT_AUDIT_TAG = "COMMENT_AUDIT";

    private CommentAuditMessaging() {
    }
}
