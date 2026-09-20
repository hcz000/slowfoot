package org.hcz.messaging.workaudit;

final class WorkAuditMessaging {

    static final String SUBMITTED_TOPIC = "${rocketmq.topics.audit-submitted}";
    static final String COMPLETED_TOPIC = "${rocketmq.topics.audit-completed}";
    static final String WORK_AUDIT_TAG = "WORK_AUDIT";

    private WorkAuditMessaging() {
    }
}
