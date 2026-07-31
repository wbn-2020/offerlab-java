package com.offerlab.community.feed.infrastructure;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.feed.api.dto.FeedFeedbackPreferenceVO;
import com.offerlab.community.feed.infrastructure.persistence.mapper.FeedFeedbackPreferenceMapper;
import com.offerlab.community.feed.infrastructure.persistence.po.FeedFeedbackPreferencePO;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class FeedFeedbackStore {

    private static final Duration FEEDBACK_TTL = Duration.ofDays(90);
    private static final Duration CONTROL_DURATION = Duration.ofDays(30);
    private static final String ACTION_HIDE = "HIDE";
    private static final String ACTION_LESS_LIKE_THIS = "LESS_LIKE_THIS";
    private static final String ACTION_RESTORE = "RESTORE";
    private static final String TARGET_POST = "POST";
    private static final String TARGET_DOMAIN = "DOMAIN";
    private static final int MAX_FEEDBACK_ENTRIES = 2000;
    private static final int MAX_HIDDEN_POST_IDS = 1000;
    private static final int MAX_REDUCED_DOMAINS = 32;
    private static final int MAX_REASON_LENGTH = 200;

    private final RedisTemplate<String, Object> redisTemplate;
    private final FeedFeedbackPreferenceMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private volatile boolean tableReadyConfirmed;

    /**
     * Production constructor. The one-argument constructor is intentionally kept for
     * isolated feed tests that only exercise the legacy Redis adapter behavior.
     */
    @Autowired
    public FeedFeedbackStore(RedisTemplate<String, Object> redisTemplate,
                             FeedFeedbackPreferenceMapper mapper,
                             SnowflakeIdGenerator idGenerator) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.idGenerator = idGenerator;
    }

    public FeedFeedbackStore(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, null, null);
    }

    public void record(Long uid, Long postId, String action, String reason) {
        record(uid, postId, action, reason, null);
    }

    public void record(Long uid, Long postId, String action, String reason, Integer domain) {
        if (uid == null || uid <= 0 || postId == null || postId <= 0) {
            return;
        }
        String normalizedAction = normalizeAction(action);
        if (ACTION_RESTORE.equals(normalizedAction)) {
            Integer restoreDomain = domain;
            if (restoreDomain == null) {
                restoreDomain = persistentTargetDomain(uid, postId);
            }
            if (restoreDomain == null) {
                restoreDomain = redisTargetDomain(uid, postId);
            }
            boolean keepDomainControl = hasOtherDomainControl(uid, postId, restoreDomain);
            deletePersistent(uid, postId);
            try {
                removeRedis(uid, postId, restoreDomain, keepDomainControl);
            } catch (RuntimeException e) {
                log.warn("feed feedback restore cache cleanup skipped, uid={}, postId={}",
                        LogMask.id(uid), LogMask.id(postId), e);
            }
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(CONTROL_DURATION);
        String targetType = ACTION_LESS_LIKE_THIS.equals(normalizedAction) && domain != null
                ? TARGET_DOMAIN
                : TARGET_POST;
        Long targetId = TARGET_DOMAIN.equals(targetType) ? domain : postId;

        boolean persistentAvailable = persistentStoreAvailable();
        if (!persistentAvailable && mapper != null && idGenerator != null) {
            throw new IllegalStateException("feed feedback persistence unavailable");
        }
        if (persistentAvailable) {
            FeedFeedbackPreferencePO preference = new FeedFeedbackPreferencePO();
            preference.setId(idGenerator.nextId());
            preference.setUid(uid);
            preference.setPostId(postId);
            preference.setAction(normalizedAction);
            preference.setTargetType(targetType);
            preference.setTargetId(targetId);
            preference.setReason(clean(reason));
            preference.setExpiresAt(expiresAt);
            preference.setCreateTime(now);
            preference.setUpdateTime(now);
            try {
                mapper.upsert(preference);
            } catch (RuntimeException e) {
                log.error("feed feedback persistence failed, uid={}, postId={}",
                        LogMask.id(uid), LogMask.id(postId), e);
                throw e;
            }
        }

        // Redis is a cache only. A Redis outage must not erase the MySQL fact.
        try {
            recordRedis(uid, postId, normalizedAction, reason, domain);
        } catch (RuntimeException e) {
            log.warn("feed feedback cache write skipped, uid={}, postId={}, action={}",
                    LogMask.id(uid), LogMask.id(postId), normalizedAction, e);
        }
    }

    public Set<Long> hiddenPostIds(Long uid) {
        if (uid == null || uid <= 0) {
            return Set.of();
        }
        if (persistentStoreAvailable()) {
            try {
                Set<Long> values = mapper.listActiveHiddenPostIds(uid, LocalDateTime.now());
                return values == null ? Set.of() : values.stream()
                        .filter(id -> id != null && id > 0)
                        .collect(Collectors.toSet());
            } catch (RuntimeException e) {
                log.warn("feed hidden preference read fell back to Redis, uid={}", LogMask.id(uid), e);
            }
        }
        return hiddenPostIdsFromRedis(uid);
    }

    public Set<Integer> lessLikedDomains(Long uid) {
        if (uid == null || uid <= 0) {
            return Set.of();
        }
        if (persistentStoreAvailable()) {
            try {
                Set<Long> values = mapper.listActiveReducedDomainIds(uid, LocalDateTime.now());
                return values == null ? Set.of() : values.stream()
                        .filter(id -> id != null && id > 0 && id <= Integer.MAX_VALUE)
                        .map(Long::intValue)
                        .collect(Collectors.toSet());
            } catch (RuntimeException e) {
                log.warn("feed reduced-domain preference read fell back to Redis, uid={}", LogMask.id(uid), e);
            }
        }
        return lessLikedDomainsFromRedis(uid);
    }

    public PageResult<FeedFeedbackPreferenceVO> list(Long uid, String cursor, int size) {
        if (uid == null || uid <= 0) {
            return PageResult.empty();
        }
        int pageSize = Math.min(Math.max(size, 1), 50);
        if (!persistentStoreAvailable()) {
            return PageResult.<FeedFeedbackPreferenceVO>empty()
                    .withMetadata("redis-only", true, "FEEDBACK_PERSISTENCE_UNAVAILABLE", null);
        }
        Cursor decoded = Cursor.parse(cursor);
        try {
            var rows = mapper.listActive(uid, LocalDateTime.now(), decoded.time(), decoded.id(), pageSize + 1);
            boolean hasMore = rows.size() > pageSize;
            var items = rows.stream()
                    .limit(pageSize)
                    .map(FeedFeedbackStore::toVO)
                    .toList();
            String nextCursor = hasMore && !items.isEmpty() ? Cursor.of(rows.get(pageSize - 1)) : null;
            return PageResult.of(items, nextCursor, hasMore);
        } catch (RuntimeException e) {
            log.warn("feed feedback preference list fell back to empty, uid={}", LogMask.id(uid), e);
            return PageResult.<FeedFeedbackPreferenceVO>empty()
                    .withMetadata("feedback-unavailable", true, "FEEDBACK_READ_UNAVAILABLE", null);
        }
    }

    public FeedFeedbackPreferenceVO find(Long uid, Long postId) {
        if (uid == null || uid <= 0 || postId == null || postId <= 0 || !persistentStoreAvailable()) {
            return null;
        }
        try {
            return toVO(mapper.findActive(uid, postId, LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("feed feedback preference read failed, uid={}, postId={}",
                    LogMask.id(uid), LogMask.id(postId), e);
            return null;
        }
    }

    private boolean persistentStoreAvailable() {
        if (mapper == null || idGenerator == null) {
            return false;
        }
        if (tableReadyConfirmed) {
            return true;
        }
        try {
            tableReadyConfirmed = mapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.debug("feed feedback table check failed", e);
            return false;
        }
        return tableReadyConfirmed;
    }

    private void deletePersistent(Long uid, Long postId) {
        boolean persistentAvailable = persistentStoreAvailable();
        if (!persistentAvailable && mapper != null && idGenerator != null) {
            throw new IllegalStateException("feed feedback persistence unavailable");
        }
        if (!persistentAvailable) {
            return;
        }
        try {
            mapper.deleteByPost(uid, postId);
        } catch (RuntimeException e) {
            log.error("feed feedback restore failed, uid={}, postId={}",
                    LogMask.id(uid), LogMask.id(postId), e);
            throw e;
        }
    }

    private void recordRedis(Long uid, Long postId, String action, String reason, Integer domain) {
        if (redisTemplate == null) {
            return;
        }
        String postIdText = String.valueOf(postId);
        var hashOps = redisTemplate.opsForHash();
        var zSetOps = redisTemplate.opsForZSet();
        hashOps.put(feedbackKey(uid), postIdText,
                action + "|" + (domain == null ? "" : domain) + "|" + clean(reason));
        zSetOps.add(feedbackOrderKey(uid), postIdText, System.currentTimeMillis());
        Set<Object> overflow = zSetOps.range(feedbackOrderKey(uid), 0, -MAX_FEEDBACK_ENTRIES - 1);
        if (overflow != null && !overflow.isEmpty()) {
            hashOps.delete(feedbackKey(uid), overflow.toArray());
            zSetOps.removeRange(feedbackOrderKey(uid), 0, -MAX_FEEDBACK_ENTRIES - 1);
        }
        redisTemplate.expire(feedbackKey(uid), FEEDBACK_TTL);
        redisTemplate.expire(feedbackOrderKey(uid), FEEDBACK_TTL);
        if (ACTION_HIDE.equals(action)) {
            zSetOps.add(hiddenKey(uid), postIdText, System.currentTimeMillis());
            zSetOps.removeRange(hiddenKey(uid), 0, -MAX_HIDDEN_POST_IDS - 1);
            redisTemplate.expire(hiddenKey(uid), FEEDBACK_TTL);
        } else if (ACTION_LESS_LIKE_THIS.equals(action) && domain != null && domain > 0) {
            String domainText = String.valueOf(domain);
            zSetOps.add(lessDomainKey(uid), domainText, System.currentTimeMillis());
            zSetOps.removeRange(lessDomainKey(uid), 0, -MAX_REDUCED_DOMAINS - 1);
            redisTemplate.expire(lessDomainKey(uid), FEEDBACK_TTL);
        }
    }

    private void removeRedis(Long uid, Long postId, Integer domain, boolean keepDomainControl) {
        if (redisTemplate == null) {
            return;
        }
        var hashOps = redisTemplate.opsForHash();
        var zSetOps = redisTemplate.opsForZSet();
        hashOps.delete(feedbackKey(uid), String.valueOf(postId));
        zSetOps.remove(hiddenKey(uid), String.valueOf(postId));
        if (domain != null && !keepDomainControl) {
            zSetOps.remove(lessDomainKey(uid), String.valueOf(domain));
        }
    }

    private boolean hasOtherDomainControl(Long uid, Long postId, Integer domain) {
        if (domain == null) {
            return false;
        }
        if (persistentStoreAvailable()) {
            try {
                return mapper.countOtherActiveDomainControls(uid, postId, domain, LocalDateTime.now()) > 0;
            } catch (RuntimeException e) {
                log.warn("feed feedback restore keeps domain cache after persistence check failed, uid={}, domain={}",
                        LogMask.id(uid), domain, e);
                return true;
            }
        }
        if (redisTemplate == null) {
            return false;
        }
        try {
            Map<Object, Object> controls = redisTemplate.opsForHash().entries(feedbackKey(uid));
            if (controls == null || controls.isEmpty()) {
                return false;
            }
            String currentPost = String.valueOf(postId);
            String domainText = String.valueOf(domain);
            return controls.entrySet().stream()
                    .filter(entry -> !currentPost.equals(String.valueOf(entry.getKey())))
                    .map(entry -> String.valueOf(entry.getValue()).split("\\|", 3))
                    .anyMatch(parts -> parts.length >= 2
                            && ACTION_LESS_LIKE_THIS.equals(parts[0])
                            && domainText.equals(parts[1]));
        } catch (RuntimeException e) {
            log.warn("feed feedback restore keeps domain cache after Redis check failed, uid={}, domain={}",
                    LogMask.id(uid), domain, e);
            return true;
        }
    }

    private Integer persistentTargetDomain(Long uid, Long postId) {
        if (!persistentStoreAvailable()) {
            return null;
        }
        try {
            FeedFeedbackPreferencePO preference = mapper.findActive(uid, postId, LocalDateTime.now());
            if (preference == null
                    || !ACTION_LESS_LIKE_THIS.equals(preference.getAction())
                    || !TARGET_DOMAIN.equals(preference.getTargetType())
                    || preference.getTargetId() == null
                    || preference.getTargetId() > Integer.MAX_VALUE) {
                return null;
            }
            return preference.getTargetId().intValue();
        } catch (RuntimeException e) {
            log.debug("feed feedback restore domain lookup skipped, uid={}, postId={}",
                    LogMask.id(uid), LogMask.id(postId), e);
            return null;
        }
    }

    private Integer redisTargetDomain(Long uid, Long postId) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            Object raw = redisTemplate.opsForHash().get(feedbackKey(uid), String.valueOf(postId));
            if (raw == null) {
                return null;
            }
            String[] parts = String.valueOf(raw).split("\\|", 3);
            if (parts.length < 2 || !ACTION_LESS_LIKE_THIS.equals(parts[0])) {
                return null;
            }
            Long domain = parseLong(parts[1]);
            return domain == null || domain <= 0 || domain > Integer.MAX_VALUE
                    ? null
                    : domain.intValue();
        } catch (RuntimeException e) {
            log.debug("feed feedback restore Redis domain lookup skipped, uid={}, postId={}",
                    LogMask.id(uid), LogMask.id(postId), e);
            return null;
        }
    }

    private Set<Long> hiddenPostIdsFromRedis(Long uid) {
        if (redisTemplate == null) {
            return Set.of();
        }
        double cutoff = System.currentTimeMillis() - CONTROL_DURATION.toMillis();
        Set<Object> values = redisTemplate.opsForZSet()
                .reverseRangeByScore(hiddenKey(uid), cutoff, Double.POSITIVE_INFINITY);
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(String::valueOf)
                .map(FeedFeedbackStore::parseLong)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    private Set<Integer> lessLikedDomainsFromRedis(Long uid) {
        if (redisTemplate == null) {
            return Set.of();
        }
        double cutoff = System.currentTimeMillis() - CONTROL_DURATION.toMillis();
        Set<Object> values = redisTemplate.opsForZSet()
                .reverseRangeByScore(lessDomainKey(uid), cutoff, Double.POSITIVE_INFINITY);
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .map(String::valueOf)
                .map(FeedFeedbackStore::parseLong)
                .filter(id -> id != null && id > 0 && id <= Integer.MAX_VALUE)
                .map(Long::intValue)
                .collect(Collectors.toSet());
    }

    private static FeedFeedbackPreferenceVO toVO(FeedFeedbackPreferencePO row) {
        if (row == null) {
            return null;
        }
        return FeedFeedbackPreferenceVO.builder()
                .postId(row.getPostId())
                .action(row.getAction())
                .targetType(row.getTargetType())
                .targetId(row.getTargetId())
                .reason(row.getReason())
                .expiresAt(row.getExpiresAt())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private static String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return ACTION_HIDE;
        }
        String normalized = action.trim().toUpperCase();
        return switch (normalized) {
            case "RESTORE", "MORE_LIKE_THIS" -> ACTION_RESTORE;
            case "LESS_LIKE_THIS" -> ACTION_LESS_LIKE_THIS;
            case "HIDE", "NOT_INTERESTED", "DISLIKE", "HIDE_AUTHOR" -> ACTION_HIDE;
            default -> ACTION_HIDE;
        };
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

    private static String lessDomainKey(Long uid) {
        return "offerlab:feed:less-domain:z:" + uid;
    }

    private record Cursor(LocalDateTime time, Long id) {
        private static Cursor parse(String cursor) {
            if (cursor == null || cursor.isBlank() || !cursor.contains(":")) {
                return new Cursor(null, null);
            }
            try {
                String[] parts = cursor.split(":", 2);
                long millis = Long.parseLong(parts[0]);
                long id = Long.parseLong(parts[1]);
                return new Cursor(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneOffset.UTC), id);
            } catch (RuntimeException e) {
                return new Cursor(null, null);
            }
        }

        private static String of(FeedFeedbackPreferencePO row) {
            if (row == null || row.getUpdateTime() == null || row.getId() == null) {
                return null;
            }
            return row.getUpdateTime().toInstant(ZoneOffset.UTC).toEpochMilli() + ":" + row.getId();
        }
    }
}
