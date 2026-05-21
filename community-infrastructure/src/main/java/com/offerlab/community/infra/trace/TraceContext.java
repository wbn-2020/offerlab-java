package com.offerlab.community.infra.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路 traceId 上下文
 * 通过 TraceFilter / AuthInterceptor 注入到 MDC
 */
public final class TraceContext {

    public static final String KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    private TraceContext() {}

    public static String ensure() {
        String existing = MDC.get(KEY);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(KEY, id);
        return id;
    }

    public static String set(String traceId) {
        MDC.put(KEY, traceId);
        return traceId;
    }

    public static String get() {
        return MDC.get(KEY);
    }

    public static void clear() {
        MDC.remove(KEY);
    }
}
