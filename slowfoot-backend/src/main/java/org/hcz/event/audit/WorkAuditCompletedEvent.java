package org.hcz.event.audit;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;


// 作品审核完成事件。
//
// 审核通过后的推流、下架后的反向清理都由监听器在事务提交后执行，
// 这样自动审核与人工复审能共用同一条副作用路径。

@Getter
public class WorkAuditCompletedEvent extends ApplicationEvent {

    private final String eventId;
    private final Long workId;
    private final Long userId;
    private final Long createdTime;

// 通过审核且本次确实产生了新的公开内容时，推入推荐池与关注流。

    private final boolean approvedForPublish;

// 从公开转为私密/下架时，从推荐池与关注流反向移除。

    private final boolean removeFromFeed;

    public WorkAuditCompletedEvent(Object source, Long workId, Long userId, Long createdTime,
                                   boolean approvedForPublish, boolean removeFromFeed) {
        super(source);
        this.eventId = UUID.randomUUID().toString();
        this.workId = workId;
        this.userId = userId;
        this.createdTime = createdTime;
        this.approvedForPublish = approvedForPublish;
        this.removeFromFeed = removeFromFeed;
    }
}
