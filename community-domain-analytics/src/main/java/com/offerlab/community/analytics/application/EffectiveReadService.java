package com.offerlab.community.analytics.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.analytics.api.dto.EffectiveReadCompleteCmd;
import com.offerlab.community.analytics.api.dto.EffectiveReadCompleteDTO;
import com.offerlab.community.analytics.api.dto.EffectiveReadHeartbeatCmd;
import com.offerlab.community.analytics.api.dto.EffectiveReadHeartbeatDTO;
import com.offerlab.community.analytics.api.dto.EffectiveReadSessionDTO;
import com.offerlab.community.analytics.api.dto.EffectiveReadSessionStartCmd;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EffectiveReadService {

    public static final int MINIMUM_ACTIVE_SECONDS = 20;
    public static final int MINIMUM_SCROLL_PERCENT = 60;
    public static final int HEARTBEAT_INTERVAL_SECONDS = 5;
    public static final int HEARTBEAT_TIMEOUT_SECONDS = 8;

    private static final String ACTIVITY_ACTIVE = "ACTIVE";
    private static final String ACTIVITY_PAUSED = "PAUSED";
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);
    private static final Duration START_LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration MUTATION_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration READER_LEASE_TTL = Duration.ofSeconds(10);
    private static final int MAX_ACTIVE_SESSIONS_PER_USER = 8;
    private static final String SESSION_KEY_PREFIX = "growth:effective-read:session:";
    private static final String ACTIVE_SESSION_KEY_PREFIX = "growth:effective-read:active:";
    private static final String ACTIVE_SESSIONS_KEY_PREFIX = "growth:effective-read:active-sessions:";
    private static final String START_LOCK_KEY_PREFIX = "growth:effective-read:start-lock:";
    private static final String MUTATION_LOCK_KEY_PREFIX = "growth:effective-read:mutation-lock:";
    private static final String READER_LEASE_KEY_PREFIX = "growth:effective-read:reader:";
    private static final String EVENT_KEY_PREFIX = "effective-read:v1:";
    private static final Pattern SESSION_TOKEN_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] "
                    + "then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> CLAIM_OR_RENEW_READER_LEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "local current = redis.call('get', KEYS[1]); "
                            + "if not current then "
                            + "redis.call('psetex', KEYS[1], ARGV[2], ARGV[1]); return 1; "
                            + "elseif current == ARGV[1] then "
                            + "redis.call('pexpire', KEYS[1], ARGV[2]); return 2; "
                            + "else return 0 end",
                    Long.class);
    private static final DefaultRedisScript<Long> FENCED_SESSION_WRITE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end; "
                            + "redis.call('psetex', KEYS[2], ARGV[3], ARGV[2]); return 1;",
                    Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final GrowthInsightMapper growthInsightMapper;
    private final GrowthEventService growthEventService;

    public EffectiveReadSessionDTO startSession(Long uid, EffectiveReadSessionStartCmd cmd) {
        requireUser(uid);
        Long postId = requirePostId(cmd == null ? null : cmd.getPostId());
        requirePublicPost(postId, uid);

        long now = System.currentTimeMillis();
        try {
            EffectiveReadSessionDTO reusable = findReusableSession(uid, postId, now);
            if (reusable != null) {
                return reusable;
            }

            String lockOwner = UUID.randomUUID().toString();
            Boolean claimed = redis.opsForValue().setIfAbsent(startLockKey(uid), lockOwner, START_LOCK_TTL);
            if (!Boolean.TRUE.equals(claimed)) {
                reusable = findReusableSession(uid, postId, System.currentTimeMillis());
                if (reusable != null) {
                    return reusable;
                }
                throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                        "effective read session creation is already in progress");
            }

            try {
                now = System.currentTimeMillis();
                reusable = findReusableSession(uid, postId, now);
                if (reusable != null) {
                    return reusable;
                }

                pruneActiveSessions(uid, now);
                Long activeCount = redis.opsForZSet().zCard(activeSessionsKey(uid));
                if (activeCount != null && activeCount >= MAX_ACTIVE_SESSIONS_PER_USER) {
                    throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                            "too many active effective read sessions");
                }

                String sessionToken = UUID.randomUUID().toString().replace("-", "");
                long startedAtEpochMillis = System.currentTimeMillis();
                long expiresAtEpochMillis = startedAtEpochMillis + SESSION_TTL.toMillis();
                EffectiveReadSessionState state = new EffectiveReadSessionState(
                        uid,
                        postId,
                        sessionToken,
                        startedAtEpochMillis,
                        expiresAtEpochMillis,
                        eventKey(uid, postId, startedAtEpochMillis),
                        0L,
                        null,
                        false,
                        0L,
                        0,
                        null);
                try {
                    redis.opsForValue().set(redisKey(sessionToken), serialize(state), SESSION_TTL);
                    redis.opsForValue().set(activeSessionKey(uid, postId), sessionToken, SESSION_TTL);
                    Boolean indexed = redis.opsForZSet().add(
                            activeSessionsKey(uid), sessionToken, expiresAtEpochMillis);
                    Boolean ttlApplied = redis.expire(activeSessionsKey(uid), SESSION_TTL);
                    if (!Boolean.TRUE.equals(indexed) || !Boolean.TRUE.equals(ttlApplied)) {
                        throw new IllegalStateException("effective read active session index was not persisted");
                    }
                } catch (RuntimeException e) {
                    discardCreatedSession(uid, postId, sessionToken);
                    throw e;
                }
                return toSessionDTO(state, startedAtEpochMillis);
            } finally {
                releaseLock(startLockKey(uid), lockOwner);
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                    "effective read session is unavailable");
        }
    }

    public EffectiveReadHeartbeatDTO heartbeat(Long uid, EffectiveReadHeartbeatCmd heartbeatCmd) {
        requireUser(uid);
        String sessionToken = requireSessionToken(
                heartbeatCmd == null ? null : heartbeatCmd.getSessionToken());
        long heartbeatSeq = requireHeartbeatSeq(
                heartbeatCmd == null ? null : heartbeatCmd.getHeartbeatSeq());
        String activityState = requireActivityState(
                heartbeatCmd == null ? null : heartbeatCmd.getActivityState());
        int scrollPercent = requireScrollPercent(
                heartbeatCmd == null ? null : heartbeatCmd.getScrollPercent());
        String lockOwner = claimMutationLock(sessionToken);

        try {
            EffectiveReadSessionState state = requireSession(sessionToken);
            validateSessionIdentity(state, uid, sessionToken);
            long now = System.currentTimeMillis();
            requireUnexpiredSession(state, now);
            if (state.completedAtEpochMillis() != null) {
                return toHeartbeatDTO(false, false, state);
            }
            if (heartbeatSeq <= state.lastHeartbeatSeq()) {
                return toHeartbeatDTO(false, state.segmentActive(), state);
            }
            if (heartbeatSeq != state.lastHeartbeatSeq() + 1L) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "effective read heartbeat sequence is ahead");
            }

            HeartbeatMutation mutation = ACTIVITY_ACTIVE.equals(activityState)
                    ? applyActiveHeartbeat(state, heartbeatSeq, scrollPercent, now)
                    : applyPausedHeartbeat(state, heartbeatSeq, scrollPercent, now);
            persistSession(mutation.state(), now, lockOwner);
            return toHeartbeatDTO(true, mutation.countingActive(), mutation.state());
        } finally {
            releaseLock(mutationLockKey(sessionToken), lockOwner);
        }
    }

    public EffectiveReadCompleteDTO complete(Long uid, EffectiveReadCompleteCmd cmd) {
        requireUser(uid);
        String sessionToken = requireSessionToken(cmd == null ? null : cmd.getSessionToken());
        String lockOwner = claimMutationLock(sessionToken);

        try {
            EffectiveReadSessionState state = requireSession(sessionToken);
            validateSessionIdentity(state, uid, sessionToken);
            long now = System.currentTimeMillis();
            requireUnexpiredSession(state, now);
            if (state.completedAtEpochMillis() != null) {
                return toCompleteDTO(true, state);
            }
            if (!isQualified(state)) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "effective read requirements are not met");
            }

            Long postId = state.postId();
            Map<String, Object> publicPost = requirePublicPost(postId, uid);
            boolean recorded = growthEventService.recordTrusted(
                    GrowthEventService.EFFECTIVE_READ,
                    state.eventKey(),
                    uid,
                    asInteger(publicPost.get("domain")),
                    postId,
                    "POST",
                    String.valueOf(postId),
                    "post.detail");
            if (!recorded) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                        "effective read event persistence is unavailable");
            }

            EffectiveReadSessionState completedState = markCompleted(state, now, lockOwner);
            return toCompleteDTO(true, completedState);
        } finally {
            releaseLock(mutationLockKey(sessionToken), lockOwner);
        }
    }

    public boolean abandon(Long uid, EffectiveReadCompleteCmd cmd) {
        requireUser(uid);
        String sessionToken = requireSessionToken(cmd == null ? null : cmd.getSessionToken());
        String lockOwner = claimMutationLock(sessionToken);

        try {
            EffectiveReadSessionState state = readSession(sessionToken);
            if (state == null) {
                try {
                    redis.opsForZSet().remove(activeSessionsKey(uid), sessionToken);
                } catch (RuntimeException e) {
                    throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                            "effective read session cleanup is unavailable");
                }
                releaseReaderLeaseQuietly(uid, sessionToken);
                return true;
            }
            validateSessionIdentity(state, uid, sessionToken);
            try {
                redis.delete(redisKey(sessionToken));
                removeActiveSession(uid, state.postId(), sessionToken);
            } catch (RuntimeException e) {
                throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                        "effective read session cleanup is unavailable");
            }
            releaseReaderLeaseQuietly(uid, sessionToken);
            return true;
        } finally {
            releaseLock(mutationLockKey(sessionToken), lockOwner);
        }
    }

    private HeartbeatMutation applyActiveHeartbeat(
            EffectiveReadSessionState state, long heartbeatSeq, int scrollPercent, long now) {
        long leaseStatus = claimOrRenewReaderLease(state.uid(), state.sessionToken());
        boolean ownsLease = leaseStatus > 0L;
        long activeMillis = state.activeMillis();
        if (leaseStatus == 2L && state.segmentActive()) {
            activeMillis += countableGap(state.lastHeartbeatAtEpochMillis(), now);
        }
        int maxScrollPercent = ownsLease
                ? Math.max(state.maxScrollPercent(), scrollPercent)
                : state.maxScrollPercent();
        EffectiveReadSessionState next = copyState(
                state,
                heartbeatSeq,
                now,
                ownsLease,
                activeMillis,
                maxScrollPercent,
                state.completedAtEpochMillis());
        return new HeartbeatMutation(next, ownsLease);
    }

    private HeartbeatMutation applyPausedHeartbeat(
            EffectiveReadSessionState state, long heartbeatSeq, int scrollPercent, long now) {
        boolean heldLease = releaseReaderLease(state.uid(), state.sessionToken());
        long activeMillis = state.activeMillis();
        if (heldLease && state.segmentActive()) {
            activeMillis += countableGap(state.lastHeartbeatAtEpochMillis(), now);
        }
        int maxScrollPercent = heldLease
                ? Math.max(state.maxScrollPercent(), scrollPercent)
                : state.maxScrollPercent();
        EffectiveReadSessionState next = copyState(
                state,
                heartbeatSeq,
                now,
                false,
                activeMillis,
                maxScrollPercent,
                state.completedAtEpochMillis());
        return new HeartbeatMutation(next, false);
    }

    private long claimOrRenewReaderLease(Long uid, String sessionToken) {
        try {
            Long result = redis.execute(
                    CLAIM_OR_RENEW_READER_LEASE_SCRIPT,
                    List.of(readerLeaseKey(uid)),
                    sessionToken,
                    String.valueOf(READER_LEASE_TTL.toMillis()));
            if (result == null) {
                throw new IllegalStateException("reader lease script returned no result");
            }
            return result;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                    "effective read reader lease is unavailable");
        }
    }

    private boolean releaseReaderLease(Long uid, String sessionToken) {
        try {
            Long released = redis.execute(
                    COMPARE_AND_DELETE_SCRIPT,
                    List.of(readerLeaseKey(uid)),
                    sessionToken);
            if (released == null) {
                throw new IllegalStateException("reader lease release returned no result");
            }
            return released > 0L;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                    "effective read reader lease is unavailable");
        }
    }

    private long countableGap(Long previousHeartbeatAt, long now) {
        if (previousHeartbeatAt == null) {
            return 0L;
        }
        long gap = now - previousHeartbeatAt;
        return gap > 0L && gap <= Duration.ofSeconds(HEARTBEAT_TIMEOUT_SECONDS).toMillis()
                ? gap
                : 0L;
    }

    private EffectiveReadSessionDTO findReusableSession(Long uid, Long postId, long now) {
        String sessionToken = redis.opsForValue().get(activeSessionKey(uid, postId));
        if (!StringUtils.hasText(sessionToken)) {
            return null;
        }
        EffectiveReadSessionState state;
        try {
            state = readSession(sessionToken);
        } catch (BizException e) {
            if (ErrorCode.CACHE_ERROR.getCode().equals(e.getCode())) {
                throw e;
            }
            removeActiveSession(uid, postId, sessionToken);
            return null;
        }
        if (!matchesSessionIdentity(state, uid, sessionToken)
                || !postId.equals(state.postId())
                || state.completedAtEpochMillis() != null
                || state.expiresAtEpochMillis() <= now) {
            removeActiveSession(uid, postId, sessionToken);
            releaseReaderLeaseQuietly(uid, sessionToken);
            return null;
        }
        return toSessionDTO(state, now);
    }

    private void pruneActiveSessions(Long uid, long now) {
        String activeSessionsKey = activeSessionsKey(uid);
        redis.opsForZSet().removeRangeByScore(activeSessionsKey, 0, now);
        Set<String> sessionTokens = redis.opsForZSet().range(activeSessionsKey, 0, -1);
        if (sessionTokens == null || sessionTokens.isEmpty()) {
            return;
        }
        for (String sessionToken : sessionTokens) {
            EffectiveReadSessionState state;
            try {
                state = readSession(sessionToken);
            } catch (BizException e) {
                if (ErrorCode.CACHE_ERROR.getCode().equals(e.getCode())) {
                    throw e;
                }
                redis.opsForZSet().remove(activeSessionsKey, sessionToken);
                continue;
            }
            if (!matchesSessionIdentity(state, uid, sessionToken)
                    || state.completedAtEpochMillis() != null
                    || state.expiresAtEpochMillis() <= now) {
                redis.opsForZSet().remove(activeSessionsKey, sessionToken);
                if (state != null && state.postId() != null) {
                    removeActiveSession(uid, state.postId(), sessionToken);
                }
                releaseReaderLeaseQuietly(uid, sessionToken);
            }
        }
    }

    private EffectiveReadSessionState requireSession(String sessionToken) {
        EffectiveReadSessionState state = readSession(sessionToken);
        if (state == null) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "effective read session is expired");
        }
        return state;
    }

    private EffectiveReadSessionState readSession(String sessionToken) {
        String serialized;
        try {
            serialized = redis.opsForValue().get(redisKey(sessionToken));
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                    "effective read session is unavailable");
        }
        return StringUtils.hasText(serialized) ? deserialize(serialized) : null;
    }

    private void persistSession(EffectiveReadSessionState state, long now, String lockOwner) {
        long remainingMillis = state.expiresAtEpochMillis() - now;
        if (remainingMillis <= 0L) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "effective read session is expired");
        }
        try {
            Long written = redis.execute(
                    FENCED_SESSION_WRITE_SCRIPT,
                    List.of(
                            mutationLockKey(state.sessionToken()),
                            redisKey(state.sessionToken())),
                    lockOwner,
                    serialize(state),
                    String.valueOf(remainingMillis));
            if (written == null) {
                throw new IllegalStateException("effective read fenced session write returned no result");
            }
            if (written <= 0L) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "effective read mutation lease expired");
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                    "effective read session state is unavailable");
        }
    }

    private EffectiveReadSessionState markCompleted(
            EffectiveReadSessionState state, long now, String lockOwner) {
        EffectiveReadSessionState completedState = copyState(
                state,
                state.lastHeartbeatSeq(),
                state.lastHeartbeatAtEpochMillis(),
                false,
                state.activeMillis(),
                state.maxScrollPercent(),
                now);
        persistSession(completedState, now, lockOwner);
        releaseReaderLeaseQuietly(state.uid(), state.sessionToken());
        try {
            removeActiveSession(state.uid(), state.postId(), state.sessionToken());
        } catch (RuntimeException e) {
            log.warn("effective read active session cleanup failed: token={}",
                    state.sessionToken(), e);
        }
        return completedState;
    }

    private EffectiveReadSessionState copyState(
            EffectiveReadSessionState state,
            long lastHeartbeatSeq,
            Long lastHeartbeatAtEpochMillis,
            boolean segmentActive,
            long activeMillis,
            int maxScrollPercent,
            Long completedAtEpochMillis) {
        return new EffectiveReadSessionState(
                state.uid(),
                state.postId(),
                state.sessionToken(),
                state.startedAtEpochMillis(),
                state.expiresAtEpochMillis(),
                state.eventKey(),
                lastHeartbeatSeq,
                lastHeartbeatAtEpochMillis,
                segmentActive,
                Math.max(0L, activeMillis),
                Math.max(0, Math.min(100, maxScrollPercent)),
                completedAtEpochMillis);
    }

    private void requireUnexpiredSession(EffectiveReadSessionState state, long now) {
        if (state.expiresAtEpochMillis() <= now) {
            releaseReaderLeaseQuietly(state.uid(), state.sessionToken());
            try {
                removeActiveSession(state.uid(), state.postId(), state.sessionToken());
                redis.delete(redisKey(state.sessionToken()));
            } catch (RuntimeException e) {
                log.warn("effective read expired session cleanup failed: token={}",
                        state.sessionToken(), e);
            }
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "effective read session is expired");
        }
    }

    private void removeActiveSession(Long uid, Long postId, String sessionToken) {
        redis.execute(
                COMPARE_AND_DELETE_SCRIPT,
                List.of(activeSessionKey(uid, postId)),
                sessionToken);
        redis.opsForZSet().remove(activeSessionsKey(uid), sessionToken);
    }

    private void discardCreatedSession(Long uid, Long postId, String sessionToken) {
        try {
            removeActiveSession(uid, postId, sessionToken);
            redis.delete(redisKey(sessionToken));
        } catch (RuntimeException cleanupError) {
            log.warn("effective read partial session cleanup failed: token={}",
                    sessionToken, cleanupError);
        }
    }

    private String claimMutationLock(String sessionToken) {
        String lockOwner = UUID.randomUUID().toString();
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(
                    mutationLockKey(sessionToken), lockOwner, MUTATION_LOCK_TTL);
            if (!Boolean.TRUE.equals(claimed)) {
                throw new BizException(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                        "effective read mutation is already in progress");
            }
            return lockOwner;
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                    "effective read session is unavailable");
        }
    }

    private void releaseReaderLeaseQuietly(Long uid, String sessionToken) {
        try {
            redis.execute(
                    COMPARE_AND_DELETE_SCRIPT,
                    List.of(readerLeaseKey(uid)),
                    sessionToken);
        } catch (RuntimeException e) {
            log.warn("effective read reader lease release failed: uid={} token={}",
                    uid, sessionToken, e);
        }
    }

    private void releaseLock(String lockKey, String lockOwner) {
        try {
            redis.execute(COMPARE_AND_DELETE_SCRIPT, List.of(lockKey), lockOwner);
        } catch (RuntimeException e) {
            log.warn("effective read lock release failed: key={}", lockKey, e);
        }
    }

    private EffectiveReadSessionDTO toSessionDTO(
            EffectiveReadSessionState state, long now) {
        long remainingMillis = Math.max(1L, state.expiresAtEpochMillis() - now);
        int remainingSeconds = Math.toIntExact(
                Math.max(1L, (remainingMillis + 999L) / 1000L));
        return EffectiveReadSessionDTO.builder()
                .sessionToken(state.sessionToken())
                .postId(state.postId())
                .minimumActiveSeconds(MINIMUM_ACTIVE_SECONDS)
                .minimumScrollPercent(MINIMUM_SCROLL_PERCENT)
                .heartbeatIntervalSeconds(HEARTBEAT_INTERVAL_SECONDS)
                .heartbeatTimeoutSeconds(HEARTBEAT_TIMEOUT_SECONDS)
                .nextHeartbeatSeq(state.lastHeartbeatSeq() + 1L)
                .activeSeconds(activeSeconds(state))
                .maxScrollPercent(state.maxScrollPercent())
                .qualified(isQualified(state))
                .completed(state.completedAtEpochMillis() != null)
                .expiresInSeconds(remainingSeconds)
                .expiresAt(state.expiresAtEpochMillis())
                .build();
    }

    private EffectiveReadHeartbeatDTO toHeartbeatDTO(
            boolean accepted, boolean countingActive, EffectiveReadSessionState state) {
        return EffectiveReadHeartbeatDTO.builder()
                .accepted(accepted)
                .countingActive(countingActive)
                .nextHeartbeatSeq(state.lastHeartbeatSeq() + 1L)
                .activeSeconds(activeSeconds(state))
                .maxScrollPercent(state.maxScrollPercent())
                .qualified(isQualified(state))
                .completed(state.completedAtEpochMillis() != null)
                .expiresAt(state.expiresAtEpochMillis())
                .build();
    }

    private EffectiveReadCompleteDTO toCompleteDTO(
            boolean recorded, EffectiveReadSessionState state) {
        return EffectiveReadCompleteDTO.builder()
                .recorded(recorded)
                .completed(state.completedAtEpochMillis() != null)
                .activeSeconds(activeSeconds(state))
                .maxScrollPercent(state.maxScrollPercent())
                .build();
    }

    private static int activeSeconds(EffectiveReadSessionState state) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, state.activeMillis() / 1000L));
    }

    private static boolean isQualified(EffectiveReadSessionState state) {
        return state.activeMillis() >= Duration.ofSeconds(MINIMUM_ACTIVE_SECONDS).toMillis()
                && state.maxScrollPercent() >= MINIMUM_SCROLL_PERCENT;
    }

    private void validateSessionIdentity(
            EffectiveReadSessionState state, Long uid, String sessionToken) {
        if (!matchesSessionIdentity(state, uid, sessionToken)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "effective read session does not match this user or post");
        }
    }

    private static boolean matchesSessionIdentity(
            EffectiveReadSessionState state, Long uid, String sessionToken) {
        return state != null
                && uid.equals(state.uid())
                && sessionToken.equals(state.sessionToken())
                && state.postId() != null
                && state.postId() > 0
                && state.startedAtEpochMillis() > 0
                && state.expiresAtEpochMillis() > state.startedAtEpochMillis()
                && eventKey(uid, state.postId(), state.startedAtEpochMillis())
                .equals(state.eventKey())
                && state.lastHeartbeatSeq() >= 0
                && state.activeMillis() >= 0
                && state.maxScrollPercent() >= 0
                && state.maxScrollPercent() <= 100
                && (state.completedAtEpochMillis() == null
                || state.completedAtEpochMillis() > 0);
    }

    private Map<String, Object> requirePublicPost(Long postId, Long viewerUid) {
        Map<String, Object> post;
        try {
            post = growthInsightMapper.selectPublicPostForEffectiveRead(postId);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "public post validation is unavailable");
        }
        if (post == null || post.isEmpty()) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        Long authorId = asLong(post.get("authorId"));
        if (authorId == null || authorId <= 0) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "public post author validation is unavailable");
        }
        if (authorId.equals(viewerUid)) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "authors cannot create effective reads for their own posts");
        }
        return post;
    }

    private String serialize(EffectiveReadSessionState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.CACHE_ERROR.getCode(),
                    "effective read session cannot be serialized");
        }
    }

    private EffectiveReadSessionState deserialize(String serialized) {
        try {
            return objectMapper.readValue(serialized, EffectiveReadSessionState.class);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "effective read session is invalid");
        }
    }

    private static String redisKey(String sessionToken) {
        return SESSION_KEY_PREFIX + sessionToken;
    }

    private static String activeSessionKey(Long uid, Long postId) {
        return ACTIVE_SESSION_KEY_PREFIX + uid + ":" + postId;
    }

    private static String activeSessionsKey(Long uid) {
        return ACTIVE_SESSIONS_KEY_PREFIX + uid;
    }

    private static String startLockKey(Long uid) {
        return START_LOCK_KEY_PREFIX + uid;
    }

    private static String mutationLockKey(String sessionToken) {
        return MUTATION_LOCK_KEY_PREFIX + sessionToken;
    }

    private static String readerLeaseKey(Long uid) {
        return READER_LEASE_KEY_PREFIX + uid;
    }

    private static String eventKey(Long uid, Long postId, long startedAtEpochMillis) {
        LocalDate eventDate = Instant.ofEpochMilli(startedAtEpochMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        return EVENT_KEY_PREFIX
                + uid
                + ":"
                + postId
                + ":"
                + DateTimeFormatter.BASIC_ISO_DATE.format(eventDate);
    }

    private static String requireSessionToken(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String sessionToken = value.trim();
        if (!SESSION_TOKEN_PATTERN.matcher(sessionToken).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return sessionToken;
    }

    private static long requireHeartbeatSeq(Long value) {
        if (value == null || value <= 0L) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static String requireActivityState(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVITY_ACTIVE.equals(normalized) && !ACTIVITY_PAUSED.equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static int requireScrollPercent(Integer value) {
        if (value == null || value < 0 || value > 100) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static Long requirePostId(Long postId) {
        if (postId == null || postId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return postId;
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record HeartbeatMutation(
            EffectiveReadSessionState state,
            boolean countingActive) {
    }

    private record EffectiveReadSessionState(
            Long uid,
            Long postId,
            String sessionToken,
            long startedAtEpochMillis,
            long expiresAtEpochMillis,
            String eventKey,
            long lastHeartbeatSeq,
            Long lastHeartbeatAtEpochMillis,
            boolean segmentActive,
            long activeMillis,
            int maxScrollPercent,
            Long completedAtEpochMillis) {
    }
}
