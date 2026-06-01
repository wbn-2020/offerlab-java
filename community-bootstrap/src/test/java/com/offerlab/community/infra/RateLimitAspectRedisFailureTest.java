package com.offerlab.community.infra;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.redis.lua.LuaScriptLoader;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.infra.web.ratelimit.RateLimitAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitAspectRedisFailureTest {

    @Test
    void ordinaryWriteApisFailOpenWhenRedisIsUnavailable() throws Throwable {
        StringRedisTemplate redis = redisDownTemplate();
        LuaScriptLoader luaLoader = scriptLoader();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = new RateLimitAspect(redis, luaLoader)
                .around(joinPoint, annotation("ordinaryWrite"));

        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    void loginAndRegisterApisFailClosedWhenRedisIsUnavailable() throws Throwable {
        StringRedisTemplate redis = redisDownTemplate();
        LuaScriptLoader luaLoader = scriptLoader();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);

        BizException error = assertThrows(BizException.class, () ->
                new RateLimitAspect(redis, luaLoader).around(joinPoint, annotation("authWrite")));

        assertEquals(ErrorCode.CACHE_ERROR.getCode(), error.getCode());
        verify(joinPoint, never()).proceed();
    }

    @SuppressWarnings("unchecked")
    private static StringRedisTemplate redisDownTemplate() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(redis)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        return redis;
    }

    @SuppressWarnings("unchecked")
    private static LuaScriptLoader scriptLoader() {
        LuaScriptLoader luaLoader = mock(LuaScriptLoader.class);
        when(luaLoader.get("ratelimit_sliding")).thenReturn(mock(RedisScript.class));
        return luaLoader;
    }

    private static RateLimit annotation(String methodName) throws NoSuchMethodException {
        return SampleEndpoints.class.getDeclaredMethod(methodName).getAnnotation(RateLimit.class);
    }

    static class SampleEndpoints {
        @RateLimit(key = "ordinary-write", failOpen = true)
        void ordinaryWrite() {
        }

        @RateLimit(key = "auth-write", failOpen = false)
        void authWrite() {
        }
    }
}
