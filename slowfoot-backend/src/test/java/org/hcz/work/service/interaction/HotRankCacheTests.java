package org.hcz.work.service.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hcz.work.service.interaction.HotRankCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.hcz.common.constant.RedisKeys;
import org.hcz.work.dto.WorkRankItem;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotRankCacheTests {

    private static final long TARGET_WORK_ID = 42L;

    @Mock private RedisTemplate redisTemplate;
    @Mock private RedisConnection connection;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HotRankCache hotRankCache;

    @BeforeEach
    void setUp() {
        hotRankCache = new HotRankCache(redisTemplate, objectMapper);
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation ->
                ((RedisCallback<?>) invocation.getArgument(0)).doInRedis(connection));
    }

    @Test
    void removesEveryJsonMemberForTheWorkWithoutTouchingOtherMembers() throws Exception {
        byte[] oldTitle = rankMember(TARGET_WORK_ID, "old title");
        byte[] currentTitle = rankMember(TARGET_WORK_ID, "current title");
        byte[] anotherWork = rankMember(7L, "another work");
        byte[] malformed = "not-json".getBytes(StandardCharsets.UTF_8);
        Set<byte[]> members = new LinkedHashSet<>();
        members.add(oldTitle);
        members.add(currentTitle);
        members.add(anotherWork);
        members.add(malformed);
        when(connection.zRange(any(byte[].class), eq(0L), eq(-1L))).thenReturn(members);

        hotRankCache.removeWork(TARGET_WORK_ID);

        ArgumentCaptor<byte[]> memberCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(connection, times(2)).zRem(any(byte[].class), memberCaptor.capture());
        Set<String> removed = new LinkedHashSet<>();
        memberCaptor.getAllValues().forEach(member -> removed.add(new String(member, StandardCharsets.UTF_8)));
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(
                new String(oldTitle, StandardCharsets.UTF_8),
                new String(currentTitle, StandardCharsets.UTF_8)), removed);
    }

    @Test
    void leavesTheRankUntouchedWhenNoMemberMatches() throws Exception {
        byte[] otherWork = rankMember(7L, "another work");
        when(connection.zRange(any(byte[].class), eq(0L), eq(-1L))).thenReturn(Set.of(otherWork));

        hotRankCache.removeWork(TARGET_WORK_ID);

        verify(connection).zRange(RedisKeys.HOT_RANK.getBytes(StandardCharsets.UTF_8), 0, -1);
        org.mockito.Mockito.verifyNoMoreInteractions(connection);
    }

    private byte[] rankMember(long workId, String title) throws Exception {
        WorkRankItem item = new WorkRankItem(null, workId, title);
        return objectMapper.writeValueAsBytes(item);
    }
}
