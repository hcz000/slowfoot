package org.hcz.common.messaging.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.hcz.common.messaging.entity.EventOutbox;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {

    @Select("""
            SELECT id, event_id, event_type, topic, tag, payload, status, retry_count,
            next_retry_at, locked_until, last_error, created_at, updated_at
            FROM event_outbox
            WHERE ((status = 0 AND next_retry_at <= #{now})
               OR (status = 1 AND locked_until < #{now}))
            ORDER BY id LIMIT #{limit}""")
    List<EventOutbox> findDispatchable(@Param("now") Date now, @Param("limit") int limit);

    /** Claim one row. Only the winner may publish it. */
    @Update("""
            UPDATE event_outbox SET status = 1, retry_count = retry_count + 1,
            locked_until = #{lockedUntil}, updated_at = NOW()
            WHERE id = #{id} AND ((status = 0 AND next_retry_at <= #{now})
            OR (status = 1 AND locked_until < #{now}))""")
    int claim(@Param("id") Long id, @Param("now") Date now, @Param("lockedUntil") Date lockedUntil);

    @Update("""
            UPDATE event_outbox SET status = 2, locked_until = NULL, last_error = NULL,
            updated_at = NOW() WHERE id = #{id} AND status = 1""")
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE event_outbox SET status = #{status}, locked_until = NULL,
            next_retry_at = #{nextRetryAt}, last_error = #{lastError}, updated_at = NOW()
            WHERE id = #{id} AND status = 1""")
    int markFailure(@Param("id") Long id, @Param("status") int status,
                    @Param("nextRetryAt") Date nextRetryAt, @Param("lastError") String lastError);
}

