package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthInterceptorRedisFailureTest {

    @Test
    void protectedApiFailsClosedWhenRedisRevocationCheckFails() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        JwtService jwtService = new JwtService(redis);
        ReflectionTestUtils.setField(jwtService, "secret",
                "offerlab-test-secret-key-please-change-in-prod-1234567890abcdef");
        ReflectionTestUtils.setField(jwtService, "ttlHours", 1L);
        String token = jwtService.issue(77L);
        MockMvc mvc = ApiTestSupport.mvc(new ProtectedProbeController(), jwtService);

        mvc.perform(get("/api/v1/protected/probe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void adminSensitiveApiFailsClosedWhenRedisRevocationCheckFails() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        JwtService jwtService = new JwtService(redis);
        ReflectionTestUtils.setField(jwtService, "secret",
                "offerlab-test-secret-key-please-change-in-prod-1234567890abcdef");
        ReflectionTestUtils.setField(jwtService, "ttlHours", 1L);
        String token = jwtService.issue(88L);
        AtomicInteger entered = new AtomicInteger();
        MockMvc mvc = ApiTestSupport.mvc(new OpsProbeController(entered), jwtService);

        mvc.perform(get("/api/v1/ops/probe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
        assertEquals(0, entered.get());
    }

    @RestController
    static class ProtectedProbeController {
        @GetMapping("/api/v1/protected/probe")
        Result<Map<String, Long>> probe() {
            return Result.ok(Map.of("uid", UserContext.require()));
        }
    }

    @RestController
    static class OpsProbeController {
        private final AtomicInteger entered;

        OpsProbeController(AtomicInteger entered) {
            this.entered = entered;
        }

        @GetMapping("/api/v1/ops/probe")
        Result<Map<String, Long>> probe() {
            entered.incrementAndGet();
            return Result.ok(Map.of("uid", UserContext.require()));
        }
    }
}
