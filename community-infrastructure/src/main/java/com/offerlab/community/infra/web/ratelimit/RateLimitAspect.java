package com.offerlab.community.infra.web.ratelimit;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
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
 * key 支持 SpEL：#uid、#cmd.uid 等方法上下文变量
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
            // 控制器通常在方法体内才读取 uid，这里提前注入便捷变量供限流 key 使用。
            Long uid = UserContext.get();
            ctx.setVariable("uid", uid);
            Expression expr = parser.parseExpression(spel);
            Object value = expr.getValue(ctx);
            // key 解析失败必须阻断请求，避免所有用户退化到同一个字面量限流桶。
            if (value == null || String.valueOf(value).isBlank()) {
                throw new IllegalStateException("Rate limit key resolved to blank value");
            }
            return String.valueOf(value);
        } catch (Exception e) {
            log.error("resolve rate limit key failed: spel={}", spel, e);
            throw new SystemException("Resolve rate limit key failed", e);
        }
    }
}
