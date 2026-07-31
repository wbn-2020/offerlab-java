package com.offerlab.community.infra.web.ratelimit;

import com.offerlab.community.infra.redis.lua.LuaScriptLoader;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitAspectMemberTest {

    @Test
    void everyRequestUsesADistinctSortedSetMemberEvenWithinTheSameMillisecond() throws Throwable {
        RecordingRedisTemplate redis = new RecordingRedisTemplate();
        RateLimitAspect aspect = new RateLimitAspect(redis, new TestLuaScriptLoader());
        RateLimit annotation = SampleEndpoint.class.getDeclaredMethod("write").getAnnotation(RateLimit.class);
        ProceedingJoinPoint joinPoint = joinPoint();

        aspect.around(joinPoint, annotation);
        aspect.around(joinPoint, annotation);

        assertEquals(2, redis.arguments.size());
        List<String> first = redis.arguments.get(0);
        List<String> second = redis.arguments.get(1);
        assertEquals(5, first.size());
        assertEquals(5, second.size());
        assertTrue(first.get(4).startsWith(first.get(0) + ":"));
        assertTrue(second.get(4).startsWith(second.get(0) + ":"));
        assertNotEquals(first.get(4), second.get(4));
    }

    @Test
    void luaUsesTheUniqueMemberInsteadOfTheTimestampAsMember() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/lua/ratelimit_sliding.lua")) {
            bytes = input.readAllBytes();
        }
        String script = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(script.contains("local member = ARGV[5]"));
        assertTrue(script.contains("redis.call('ZADD', KEYS[1], now, member)"));
    }

    private static ProceedingJoinPoint joinPoint() {
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[]{ProceedingJoinPoint.class},
                (proxy, method, args) -> "proceed".equals(method.getName()) ? "ok" : null
        );
    }

    private static final class RecordingRedisTemplate extends StringRedisTemplate {

        private final List<List<String>> arguments = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            arguments.add(Arrays.stream(args).map(String::valueOf).toList());
            return (T) Long.valueOf(1L);
        }
    }

    private static final class TestLuaScriptLoader extends LuaScriptLoader {

        private final RedisScript<Long> script = new DefaultRedisScript<>("return 1", Long.class);

        @Override
        public RedisScript<Long> get(String name) {
            return script;
        }
    }

    static class SampleEndpoint {

        @RateLimit(key = "same-millisecond", rate = 10, per = 60)
        void write() {
        }
    }
}
