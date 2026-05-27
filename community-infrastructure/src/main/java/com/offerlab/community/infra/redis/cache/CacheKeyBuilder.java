package com.offerlab.community.infra.redis.cache;

/**
 * 统一 Redis Key 命名规范
 * 格式：{业务域}:{业务类型}:{资源标识}[:{子维度}]
 */
public final class CacheKeyBuilder {

    private CacheKeyBuilder() {
    }

    // ==================== User 域 ====================

    /**
     * 用户资料缓存
     * user:profile:{uid}
     */
    public static String userProfile(Long uid) {
        return "user:profile:" + uid;
    }

    /**
     * 用户级计数器
     * user:counter:{uid}
     */
    public static String userCounter(Long uid) {
        return "user:counter:" + uid;
    }

    // ==================== Post 域 ====================

    /**
     * 帖子详情缓存
     * post:detail:{postId}
     */
    public static String postDetail(Long postId) {
        return "post:detail:" + postId;
    }

    /**
     * 问题题库列表缓存
     * question:list:{hash}
     */
    public static String questionList(String hash) {
        return "question:list:" + hash;
    }

    /**
     * 问题详情缓存
     * question:detail:{questionId}
     */
    public static String questionDetail(Long questionId) {
        return "question:detail:" + questionId;
    }

    /**
     * 公司准备包缓存
     * question:company-prep:{company}
     */
    public static String companyPrep(String company) {
        return "question:company-prep:" + company;
    }

    /**
     * 帖子计数器（Hash）
     * post:counter:{postId}
     */
    public static String postCounter(Long postId) {
        return "post:counter:" + postId;
    }

    /**
     * 帖子点赞 Bitmap
     * post:like:bm:{postId}
     */
    public static String postLikeBitmap(Long postId) {
        return "post:like:bm:" + postId;
    }

    // ==================== 分布式锁 ====================

    /**
     * 缓存击穿保护锁
     * lock:cache:{key}
     */
    public static String cacheLock(String key) {
        return "lock:cache:" + key;
    }

    // ==================== Pub/Sub Channel ====================

    /**
     * 缓存失效广播 Channel
     * channel:cache:evict
     */
    public static String cacheEvictChannel() {
        return "channel:cache:evict";
    }
}
