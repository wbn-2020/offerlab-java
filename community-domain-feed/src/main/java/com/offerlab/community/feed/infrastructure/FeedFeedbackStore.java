package com.offerlab.community.feed.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FeedFeedbackStore {

    private static final Duration FEEDBACK_TTL = Duration.ofDays(90);
    private static final String ACTION_NOT_INTERESTED = "not_interested";
    private static final int MAX_FEEDBACK_ENTRIES = 2000;
    private static final int MAX_HIDDEN_POST_IDS = 1000;
    private static final int MAX_REASON_LENGTH = 200;

    private final RedisTemplate<String, Object> redisTemplate;

    public void record(Long uid, Long postId, String action, String reason) {
        if (uid == null || postId == null) {
            return;
        }
        String normalizedAction = normalizeAction(action);
        String feedbackKey = feedbackKey(uid);
        String feedbackOrderKey = feedbackOrderKey(uid);
        String postIdText = String.valueOf(postId);
        var hashOps = redisTemplate.opsForHash();
        var zSetOps = redisTemplate.opsForZSet();
        hashOps.put(feedbackKey, postIdText, normalizedAction + "|" + clean(reason));
        zSetOps.add(feedbackOrderKey, postIdText, System.currentTimeMillis());
        Set<Object> overflow = zSetOps.range(feedbackOrderKey, 0, -MAX_FEEDBACK_ENTRIES - 1);
        if (overflow != null && !overflow.isEmpty()) {
            hashOps.delete(feedbackKey, overflow.toArray());
            zSetOps.removeRange(feedbackOrderKey, 0, -MAX_FEEDBACK_ENTRIES - 1);
        }
        redisTemplate.expire(feedbackKey, FEEDBACK_TTL);
        redisTemplate.expire(feedbackOrderKey, FEEDBACK_TTL);
        if (ACTION_NOT_INTERESTED.equals(normalizedAction)) {
            String key = hiddenKey(uid);
            zSetOps.add(key, postIdText, System.currentTimeMillis());
            zSetOps.removeRange(key, 0, -MAX_HIDDEN_POST_IDS - 1);
            redisTemplate.expire(key, FEEDBACK_TTL);
        }
    }

    public Set<Long> hiddenPostIds(Long uid) {
        if (uid == null) {
            return Set.of();
        }
        Set<Object> values = redisTemplate.opsForZSet().reverseRange(hiddenKey(uid), 0, MAX_HIDDEN_POST_IDS - 1);
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(String::valueOf)
                .map(FeedFeedbackStore::parseLong)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    private static String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return ACTION_NOT_INTERESTED;
        }
        String normalized = action.trim().toLowerCase();
        return "hide".equals(normalized)
                || "dislike".equals(normalized)
                || "less_like_this".equals(normalized)
                || "hide_author".equals(normalized)
                ? ACTION_NOT_INTERESTED
                : normalized;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_REASON_LENGTH
                ? normalized
                : normalized.substring(0, MAX_REASON_LENGTH);
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String feedbackKey(Long uid) {
        return "offerlab:feed:feedback:" + uid;
    }

    private static String feedbackOrderKey(Long uid) {
        return "offerlab:feed:feedback:order:z:" + uid;
    }

    private static String hiddenKey(Long uid) {
        return "offerlab:feed:hidden:z:" + uid;
    }
}
