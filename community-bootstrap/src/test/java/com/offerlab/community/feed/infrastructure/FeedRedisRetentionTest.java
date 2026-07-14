package com.offerlab.community.feed.infrastructure;

import com.offerlab.community.infra.redis.lua.LuaScriptLoader;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedRedisRetentionTest {

    @Test
    void feedbackStoreBoundsReasonAndEvictsOverflowFromHashAndOrderIndex() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, Object> zSetOps = mock(ZSetOperations.class);
        doReturn(hashOps).when(redisTemplate).opsForHash();
        doReturn(zSetOps).when(redisTemplate).opsForZSet();
        when(zSetOps.range("offerlab:feed:feedback:order:z:7", 0, -2001))
                .thenReturn(Set.of("11"));
        FeedFeedbackStore store = new FeedFeedbackStore(redisTemplate);

        store.record(7L, 99L, "more_like_this", "x".repeat(500));

        verify(zSetOps).add(eq("offerlab:feed:feedback:order:z:7"), eq("99"), anyDouble());
        verify(zSetOps).removeRange("offerlab:feed:feedback:order:z:7", 0, -2001);
        verify(hashOps).delete("offerlab:feed:feedback:7", "11");
        verify(redisTemplate).expire("offerlab:feed:feedback:order:z:7", Duration.ofDays(90));

        var valueCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(hashOps).put(eq("offerlab:feed:feedback:7"), eq("99"), valueCaptor.capture());
        assertTrue(String.valueOf(valueCaptor.getValue()).length() <= "more_like_this|".length() + 200);
    }

    @Test
    void globalLatestRefreshesTtlAfterWrite() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        doReturn(zSetOps).when(redisTemplate).opsForZSet();
        FeedInboxRedis inboxRedis = new FeedInboxRedis(redisTemplate, mock(LuaScriptLoader.class));

        inboxRedis.addToGlobalLatest(99L, 1234L);

        verify(redisTemplate).expire("feed:latest:global", Duration.ofDays(30));
    }
}
