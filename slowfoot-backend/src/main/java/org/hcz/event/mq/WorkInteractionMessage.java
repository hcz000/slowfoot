package org.hcz.event.mq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hcz.event.work.WorkInteractionEvent;
import org.hcz.event.work.WorkInteractionType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkInteractionMessage {

    private String eventId;
    private Long workId;
    private Long userId;
    private Long workOwnerId;
    private String title;
    private List<String> labels;
    private WorkInteractionType type;
    private long countDelta;
    private double recommendationDelta;

    public static WorkInteractionMessage from(WorkInteractionEvent event) {
        return new WorkInteractionMessage(
                event.getEventId(),
                event.getWorkId(),
                event.getUserId(),
                event.getWorkOwnerId(),
                event.getTitle(),
                event.getLabels(),
                event.getType(),
                event.getCountDelta(),
                event.getRecommendationDelta());
    }
}
