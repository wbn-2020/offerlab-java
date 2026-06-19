package com.offerlab.community.infra.ops;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminOperationIdempotencyService {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(5);

    private final Clock clock;
    private final Duration ttl;
    private final IdempotencyStore store;

    public AdminOperationIdempotencyService() {
        this(Clock.systemUTC(), DEFAULT_TTL, new InMemoryIdempotencyStore(), true);
    }

    public AdminOperationIdempotencyService(Clock clock, Duration ttl) {
        this(clock, ttl, new InMemoryIdempotencyStore(), true);
    }

    @Autowired
    public AdminOperationIdempotencyService(ObjectProvider<StringRedisTemplate> redisProvider,
                                            Environment environment) {
        this(
                Clock.systemUTC(),
                DEFAULT_TTL,
                store(redisProvider == null ? null : redisProvider.getIfAvailable(), environment),
                true
        );
    }

    private AdminOperationIdempotencyService(Clock clock, Duration ttl, IdempotencyStore store, boolean ignored) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
        this.store = Objects.requireNonNull(store, "store");
    }

    public String requireKey(String idempotencyKey) {
        return cleanKey(idempotencyKey);
    }

    public String issuePreview(Long operatorUid, String operation, Collection<?> resourceIds) {
        long now = clock.millis();
        String nonce = UUID.randomUUID().toString();
        store.savePreview(nonce, new PreviewRecord(operatorUid, operation, normalizedIds(resourceIds), now + PREVIEW_TTL.toMillis()), PREVIEW_TTL);
        return nonce;
    }

    public String requirePreview(Long operatorUid, String operation, Collection<?> resourceIds, String previewNonce) {
        String nonce = cleanPreviewNonce(previewNonce);
        long now = clock.millis();
        PreviewRecord record = store.getPreview(nonce);
        if (record == null || record.expiresAt() <= now) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "previewNonce is expired or missing; preview the batch again");
        }
        String ids = normalizedIds(resourceIds);
        if (!Objects.equals(record.operatorUid(), operatorUid)
                || !Objects.equals(record.operation(), operation)
                || !Objects.equals(record.resourceIds(), ids)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "previewNonce does not match the submitted batch",
                    Map.of(
                            "errorCategory", "PREVIEW_NONCE_MISMATCH",
                            "operation", operation,
                            "previewOperation", record.operation()
                    ));
        }
        return nonce;
    }

    public void requireFresh(Long operatorUid, String operation, Collection<?> resourceIds, String idempotencyKey) {
        String normalizedKey = cleanKey(idempotencyKey);
        String fingerprint = fingerprint(operatorUid, operation, resourceIds, normalizedKey);
        long now = clock.millis();
        long expiresAt = now + ttl.toMillis();
        long existingExpiresAt = store.putFingerprintIfAbsent(fingerprint, expiresAt, ttl);
        if (existingExpiresAt > now) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "duplicate admin operation blocked by idempotency key",
                    Map.of(
                            "errorCategory", "DUPLICATE_ADMIN_OPERATION",
                            "operation", operation,
                            "idempotencyKey", normalizedKey,
                            "retryAfterSeconds", Math.max(1L, (existingExpiresAt - now + 999L) / 1000L)
                    ));
        }
    }

    private static String fingerprint(Long operatorUid, String operation, Collection<?> resourceIds, String cleanKey) {
        return String.valueOf(operatorUid) + "|" + operation + "|" + normalizedIds(resourceIds) + "|" + cleanKey;
    }

    private static String normalizedIds(Collection<?> resourceIds) {
        return resourceIds == null ? "" : resourceIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String cleanKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "idempotencyKey is required for batch admin operations");
        }
        String clean = idempotencyKey.trim();
        if (clean.length() < 8 || clean.length() > 80) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "idempotencyKey length must be between 8 and 80");
        }
        return clean;
    }

    private static String cleanPreviewNonce(String previewNonce) {
        if (!StringUtils.hasText(previewNonce)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "previewNonce is required for batch admin operations");
        }
        String clean = previewNonce.trim();
        if (clean.length() < 8 || clean.length() > 80) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "previewNonce length must be between 8 and 80");
        }
        return clean;
    }

    private record PreviewRecord(Long operatorUid, String operation, String resourceIds, long expiresAt) {
    }

    private interface IdempotencyStore {
        void savePreview(String nonce, PreviewRecord record, Duration ttl);

        PreviewRecord getPreview(String nonce);

        long putFingerprintIfAbsent(String fingerprint, long expiresAt, Duration ttl);
    }

    private static IdempotencyStore store(StringRedisTemplate redis, Environment environment) {
        if (redis != null) {
            return new RedisIdempotencyStore(redis);
        }
        if (!isLocalLike(environment)) {
            throw new IllegalStateException("AdminOperationIdempotencyService requires Redis outside local/dev/test profiles");
        }
        return new InMemoryIdempotencyStore();
    }

    private static boolean isLocalLike(Environment environment) {
        if (environment == null) {
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.equals("local") || profile.equals("dev") || profile.equals("test"));
    }

    private static final class InMemoryIdempotencyStore implements IdempotencyStore {
        private final Map<String, Long> fingerprints = new ConcurrentHashMap<>();
        private final Map<String, PreviewRecord> previews = new ConcurrentHashMap<>();

        @Override
        public void savePreview(String nonce, PreviewRecord record, Duration ttl) {
            cleanup(System.currentTimeMillis());
            previews.put(nonce, record);
        }

        @Override
        public PreviewRecord getPreview(String nonce) {
            cleanup(System.currentTimeMillis());
            return previews.get(nonce);
        }

        @Override
        public long putFingerprintIfAbsent(String fingerprint, long expiresAt, Duration ttl) {
            cleanup(System.currentTimeMillis());
            Long existing = fingerprints.putIfAbsent(fingerprint, expiresAt);
            if (existing == null) {
                return 0L;
            }
            if (existing <= System.currentTimeMillis()) {
                fingerprints.put(fingerprint, expiresAt);
                return 0L;
            }
            return existing;
        }

        private void cleanup(long now) {
            fingerprints.entrySet().removeIf(entry -> entry.getValue() <= now);
            previews.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }
    }

    private static final class RedisIdempotencyStore implements IdempotencyStore {
        private static final String PREVIEW_PREFIX = "offerlab:admin-op:preview:";
        private static final String FINGERPRINT_PREFIX = "offerlab:admin-op:fingerprint:";

        private final StringRedisTemplate redis;

        private RedisIdempotencyStore(StringRedisTemplate redis) {
            this.redis = redis;
        }

        @Override
        public void savePreview(String nonce, PreviewRecord record, Duration ttl) {
            String value = String.join("|",
                    String.valueOf(record.operatorUid()),
                    escape(record.operation()),
                    escape(record.resourceIds()),
                    String.valueOf(record.expiresAt()));
            Boolean ok = redis.opsForValue().setIfAbsent(PREVIEW_PREFIX + nonce, value, ttl);
            if (!Boolean.TRUE.equals(ok)) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                        "duplicate admin preview nonce",
                        Map.of("errorCategory", "DUPLICATE_ADMIN_PREVIEW"));
            }
        }

        @Override
        public PreviewRecord getPreview(String nonce) {
            String value = redis.opsForValue().get(PREVIEW_PREFIX + nonce);
            return parsePreview(value);
        }

        @Override
        public long putFingerprintIfAbsent(String fingerprint, long expiresAt, Duration ttl) {
            String key = FINGERPRINT_PREFIX + fingerprint;
            Boolean ok = redis.opsForValue().setIfAbsent(key, String.valueOf(expiresAt), ttl);
            if (Boolean.TRUE.equals(ok)) {
                return 0L;
            }
            String existing = redis.opsForValue().get(key);
            try {
                return existing == null ? expiresAt : Long.parseLong(existing);
            } catch (NumberFormatException e) {
                return expiresAt;
            }
        }

        private static PreviewRecord parsePreview(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            String[] parts = value.split("\\|", -1);
            if (parts.length != 4) {
                return null;
            }
            try {
                return new PreviewRecord(
                        Long.valueOf(parts[0]),
                        unescape(parts[1]),
                        unescape(parts[2]),
                        Long.parseLong(parts[3])
                );
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static String escape(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("|", "\\p");
        }

        private static String unescape(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            StringBuilder out = new StringBuilder(value.length());
            boolean escaping = false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (escaping) {
                    out.append(c == 'p' ? '|' : c);
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else {
                    out.append(c);
                }
            }
            if (escaping) {
                out.append('\\');
            }
            return out.toString();
        }
    }
}
