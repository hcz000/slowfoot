package org.hcz.common.messaging.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventConsumeMapper {

    @Insert("INSERT IGNORE INTO event_consume(event_id, consumer, created_at) VALUES(#{eventId}, #{consumer}, NOW())")
    int insertIgnore(@Param("eventId") String eventId, @Param("consumer") String consumer);

    @Delete("DELETE FROM event_consume WHERE event_id = #{eventId} AND consumer = #{consumer}")
    int delete(@Param("eventId") String eventId, @Param("consumer") String consumer);
}
