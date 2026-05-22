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

    private final RedisTemplate<String, Object> redisTemplate;

    public void record(Long uid, Long postId, String action, String reason) {
        if (uid == null || postId == null) {
            return;
        }
        String normalizedAction = normalizeAction(action);
        redisTemplate.opsForHash().put(feedbackKey(uid), String.valueOf(postId),
                normalizedAction + "|" + clean(reason));
        redisTemplate.expire(feedbackKey(uid), FEEDBACK_TTL);
        if (ACTION_NOT_INTERESTED.equals(normalizedAction)) {
            redisTemplate.opsForSet().add(hiddenKey(uid), String.valueOf(postId));
            redisTemplate.expire(hiddenKey(uid), FEEDBACK_TTL);
        }
    }

    public Set<Long> hiddenPostIds(Long uid) {
        if (uid == null) {
            return Set.of();
        }
        Set<Object> values = redisTemplate.opsForSet().members(hiddenKey(uid));
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
        return "hide".equals(normalized) || "dislike".equals(normalized) ? ACTION_NOT_INTERESTED : normalized;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
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

    private static String hiddenKey(Long uid) {
        return "offerlab:feed:hidden:" + uid;
    }
}
