package org.hcz.messaging.notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private String eventId;
    private Long userId;
    private Long actorId;
    private String type;
    private Long targetId;
    private Long workId;
    private String content;

    public static NotificationMessage from(NotificationEvent event) {
        return new NotificationMessage(
                event.getEventId(),
                event.getUserId(),
                event.getActorId(),
                event.getType(),
                event.getTargetId(),
                event.getWorkId(),
                event.getContent());
    }
}
