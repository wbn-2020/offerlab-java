package com.offerlab.community.infra.ops;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
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
    private final Map<String, Long> fingerprints = new ConcurrentHashMap<>();
    private final Map<String, PreviewRecord> previews = new ConcurrentHashMap<>();

    public AdminOperationIdempotencyService() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    public AdminOperationIdempotencyService(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
    }

    public String requireKey(String idempotencyKey) {
        return cleanKey(idempotencyKey);
    }

    public String issuePreview(Long operatorUid, String operation, Collection<Long> resourceIds) {
        long now = clock.millis();
        cleanup(now);
        String nonce = UUID.randomUUID().toString();
        previews.put(nonce, new PreviewRecord(operatorUid, operation, normalizedIds(resourceIds), now + PREVIEW_TTL.toMillis()));
        return nonce;
    }

    public String requirePreview(Long operatorUid, String operation, Collection<Long> resourceIds, String previewNonce) {
        String nonce = cleanPreviewNonce(previewNonce);
        long now = clock.millis();
        cleanup(now);
        PreviewRecord record = previews.get(nonce);
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

    public void requireFresh(Long operatorUid, String operation, Collection<Long> resourceIds, String idempotencyKey) {
        String normalizedKey = cleanKey(idempotencyKey);
        String fingerprint = fingerprint(operatorUid, operation, resourceIds, normalizedKey);
        long now = clock.millis();
        cleanup(now);
        Long expiresAt = fingerprints.putIfAbsent(fingerprint, now + ttl.toMillis());
        if (expiresAt != null && expiresAt > now) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                    "duplicate admin operation blocked by idempotency key",
                    Map.of(
                            "errorCategory", "DUPLICATE_ADMIN_OPERATION",
                            "operation", operation,
                            "idempotencyKey", normalizedKey,
                            "retryAfterSeconds", Math.max(1L, (expiresAt - now + 999L) / 1000L)
                    ));
        }
        if (expiresAt != null) {
            fingerprints.put(fingerprint, now + ttl.toMillis());
        }
    }

    private void cleanup(long now) {
        fingerprints.entrySet().removeIf(entry -> entry.getValue() <= now);
        previews.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private static String fingerprint(Long operatorUid, String operation, Collection<Long> resourceIds, String cleanKey) {
        return String.valueOf(operatorUid) + "|" + operation + "|" + normalizedIds(resourceIds) + "|" + cleanKey;
    }

    private static String normalizedIds(Collection<Long> resourceIds) {
        return resourceIds == null ? "" : resourceIds.stream()
                .filter(Objects::nonNull)
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
}
