package org.hcz.mapper.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventConsumeMapper {

    @Insert("INSERT IGNORE INTO event_consume(event_id, consumer, created_at) " +
            "VALUES(#{eventId}, #{consumer}, NOW())")
    int insertIgnore(@Param("eventId") String eventId, @Param("consumer") String consumer);
}
