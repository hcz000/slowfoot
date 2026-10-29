package org.hcz.common.messaging.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Durable hand-off between a database transaction and RocketMQ.
 *
 * <p>The payload is kept as JSON so the business transaction does not depend
 * on a live RocketMQ producer. Rows are claimed with a lease and retried
 * after a process crash.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("event_outbox")
public class EventOutbox {

    public static final int PENDING = 0;
    public static final int PROCESSING = 1;
    public static final int SENT = 2;
    public static final int DEAD = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String eventType;
    private String topic;
    private String tag;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryAt;
    private Date lockedUntil;
    private String lastError;
    private Date createdAt;
    private Date updatedAt;

    public boolean isDispatchable() {
        return status != null && (status == PENDING || status == PROCESSING);
    }
}

