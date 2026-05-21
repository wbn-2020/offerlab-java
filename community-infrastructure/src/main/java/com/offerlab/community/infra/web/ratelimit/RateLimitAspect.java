package com.offerlab.community.infra.web.ratelimit;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.redis.lua.LuaScriptLoader;
import com.offerlab.community.infra.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;

/**
 * 限流切面：调用 Redis Lua 滑动窗口
 * key 支持 SpEL：#cmd.uid、@userContext.require()
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redis;
    private final LuaScriptLoader luaLoader;

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNames = new DefaultParameterNameDiscoverer();

    @Around("@annotation(rl)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rl) throws Throwable {
        String key = "ratelimit:" + resolveKey(pjp, rl.key());
        long now = System.currentTimeMillis();
        long window = rl.per() * 1000L;

        Long pass = redis.execute(
                luaLoader.get("ratelimit_sliding"),
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(window),
                String.valueOf(rl.rate()),
                String.valueOf(rl.per())
        );

        if (pass == null || pass == 0L) {
            log.warn("rate limit exceeded: key={} rate={}/{}s", key, rl.rate(), rl.per());
            throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        return pjp.proceed();
    }

    private String resolveKey(ProceedingJoinPoint pjp, String spel) {
        if (!spel.contains("#") && !spel.contains("@")) {
            return spel;
        }
        try {
            MethodSignature ms = (MethodSignature) pjp.getSignature();
            Method method = ms.getMethod();
            MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(
                    null, method, pjp.getArgs(), paramNames);
            // 注入便捷变量
            Long uid = UserContext.get();
            ctx.setVariable("uid", uid);
            Expression expr = parser.parseExpression(spel);
            Object value = expr.getValue(ctx);
            return String.valueOf(value);
        } catch (Exception e) {
            log.warn("resolve rate limit key failed: spel={}, fallback to literal", spel);
            return spel;
        }
    }
}
