package org.hcz.messaging.workinteraction;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.hcz.entity.vo.Model;
import org.hcz.entity.vo.UserModel;
import org.hcz.service.recommendation.InterestPushService;
import org.springframework.stereotype.Component;

// 推荐事件监听器
@Component
@RocketMQMessageListener(
        topic = WorkInteractionMessaging.TOPIC,
        selectorExpression = WorkInteractionMessaging.INTERACTION_TAG,
        consumerGroup = "${rocketmq.consumer.groups.recommendation}")
public class RecommendationEventListener implements RocketMQListener<WorkInteractionMessage> {

    private final InterestPushService interestPushService;

    public RecommendationEventListener(InterestPushService interestPushService) {
        this.interestPushService = interestPushService;
    }

    @Override
    public void onMessage(WorkInteractionMessage event) {
        if (event.getUserId() == null || event.getRecommendationDelta() == 0.0D
                || event.getLabels().isEmpty()) {
            return;
        }

        UserModel userModel = new UserModel();
        userModel.setUserId(event.getUserId());
        userModel.setModels(event.getLabels().stream().map(label -> {
            Model model = new Model();
            model.setLabel(label);
            model.setWorkId(event.getWorkId());
            model.setScore(event.getRecommendationDelta());
            return model;
        }).toList());
        interestPushService.updateUserModel(userModel);
    }
}
