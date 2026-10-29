package org.hcz.work.event;

final class WorkInteractionMessaging {

    static final String TOPIC = "${rocketmq.topics.work-interaction}";
    static final String INTERACTION_TAG = "INTERACTION";

    private WorkInteractionMessaging() {
    }
}
