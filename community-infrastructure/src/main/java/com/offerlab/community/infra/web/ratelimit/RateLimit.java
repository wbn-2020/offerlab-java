package com.offerlab.community.infra.web.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /**
     * 限流 key，支持 SpEL 表达式
     */
    String key();

    /**
     * 限流速率
     */
    int rate() default 10;

    /**
     * 时间窗口（秒）
     */
    int per() default 60;
}
