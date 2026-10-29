package org.hcz.work.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.UUID;

// 作品互动事件
@Getter
public class WorkInteractionEvent extends ApplicationEvent {

    private final String eventId;
    private final Long workId;
    private final Long userId;
    private final Long workOwnerId;
    private final String title;
    private final List<String> labels;
    private final WorkInteractionType type;
    private final long countDelta;
    private final double recommendationDelta;

    public WorkInteractionEvent(Object source, Long workId, Long workOwnerId,
                                String title, List<String> labels, Long userId,
                                WorkInteractionType type, long countDelta) {
        super(source);
        this.eventId = UUID.randomUUID().toString();
        this.workId = workId;
        this.userId = userId;
        this.workOwnerId = workOwnerId;
        this.title = title;
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        this.type = type;
        this.countDelta = countDelta;
        this.recommendationDelta = type.getRecommendationWeight() * countDelta;
    }
}
