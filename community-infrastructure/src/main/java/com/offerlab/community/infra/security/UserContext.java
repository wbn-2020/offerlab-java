package com.offerlab.community.infra.security;

/**
 * 当前请求用户上下文（ThreadLocal）
 * 由 AuthInterceptor 注入；业务代码读取
 */
public final class UserContext {

    private static final ThreadLocal<Long> UID = new ThreadLocal<>();

    private UserContext() {}

    public static void set(Long uid) {
        UID.set(uid);
    }

    public static Long get() {
        return UID.get();
    }

    public static Long require() {
        Long uid = UID.get();
        if (uid == null) {
            throw new IllegalStateException("UserContext not set; missing AuthInterceptor?");
        }
        return uid;
    }

    public static void clear() {
        UID.remove();
    }
}
