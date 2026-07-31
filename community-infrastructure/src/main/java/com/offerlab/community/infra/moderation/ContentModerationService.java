package com.offerlab.community.infra.moderation;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueueUpsertRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
public class ContentModerationService {
    private static final int KEYWORD_PAGE_SIZE = 500;
    private static final int MAX_ENABLED_KEYWORDS = 10_000;

    public static final String SCOPE_POST = "POST";
    public static final String SCOPE_COMMENT = "COMMENT";
    public static final String SCOPE_REPORT = "REPORT";
    public static final String SCOPE_PROFILE = "PROFILE";
    public static final String SCOPE_CONTENT_SERIES = "CONTENT_SERIES";
    public static final String SCOPE_CONTACT_REQUEST = "CONTACT_REQUEST";
    public static final String SOURCE_POST = "POST";
    public static final String SOURCE_COMMENT = "COMMENT";
    public static final String SOURCE_REPORT = "REPORT";
    public static final String SOURCE_CONTACT_REQUEST = "CONTACT_REQUEST";
    public static final String SOURCE_PROFILE = "USER_PROFILE";
    public static final String SOURCE_CONTENT_SERIES = "CONTENT_SERIES";

    public record ModerationDecision(boolean reviewRequired, String action, String keyword, String summary) {
        private static ModerationDecision allow() {
            return new ModerationDecision(false, "ALLOW", null, null);
        }

        private static ModerationDecision review(String keyword, String summary) {
            return new ModerationDecision(true, "REVIEW", keyword, summary);
        }
    }

    private final ContentModerationMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<ContentModerationSourceAuthorizationHandler> sourceAuthorizationHandlers;

    @Autowired
    public ContentModerationService(ContentModerationMapper mapper,
                                    SnowflakeIdGenerator idGen,
                                    ApplicationEventPublisher events,
                                    ObjectProvider<ContentModerationSourceAuthorizationHandler> sourceAuthorizationHandlers) {
        this.mapper = mapper;
        this.idGen = idGen;
        this.events = events;
        this.sourceAuthorizationHandlers = sourceAuthorizationHandlers;
    }

    public ContentModerationService(ContentModerationMapper mapper,
                                    SnowflakeIdGenerator idGen,
                                    ApplicationEventPublisher events) {
        this(mapper, idGen, events, null);
    }

    public ModerationKeywordHit findKeywordHit(Long hitId) {
        if (hitId == null || hitId <= 0) {
            return null;
        }
        return mapper.findKeywordHitById(hitId);
    }

    public void bindRecentReviewHits(Long uid, String scope, String sourceType, Long sourceId) {
        if (uid == null || sourceId == null || sourceId <= 0) {
            return;
        }
        if (!tableExists("t_moderation_keyword_hit")) {
            return;
        }
        try {
            mapper.bindRecentReviewHits(uid, normalizeScope(scope), normalizeSourceType(sourceType), sourceId, 120);
        } catch (Exception e) {
            log.warn("moderation hit source bind failed open: scope={} sourceType={} sourceId={}",
                    scope, sourceType, LogMask.id(sourceId), e);
        }
    }

    public void reviewKeywordHit(Long hitId, String reviewStatus, Long reviewerUid, String reviewNote) {
        if (hitId == null || hitId <= 0) {
            return;
        }
        mapper.reviewKeywordHit(hitId, normalizeReviewStatus(reviewStatus), reviewerUid, limit(reviewNote, 1000));
    }

    public void requireUserCanPublish(Long uid) {
        if (uid == null) {
            return;
        }
        requireTableAvailable("t_user_moderation_state", "user moderation");
        try {
            UserModerationState state = mapper.findUserState(uid);
            LocalDateTime now = LocalDateTime.now();
            if (state != null && state.getBannedUntil() != null && state.getBannedUntil().isAfter(now)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            if (state != null && state.getMutedUntil() != null && state.getMutedUntil().isAfter(now)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("user moderation check failed closed: uid={}", LogMask.id(uid), e);
            throw moderationUnavailable();
        }
    }

    public void requireContentAllowed(String scope, String... values) {
        requireContentAllowed(null, scope, values);
    }

    public void requireContentAllowed(Long uid, String scope, String... values) {
        checkContent(uid, scope, values);
    }

    public void requireContentAllowed(Long uid, String scope, String sourceType, Long sourceId, String... values) {
        checkContent(uid, scope, sourceType, sourceId, values);
    }

    public ModerationDecision checkContent(Long uid, String scope, String... values) {
        return checkContent(uid, scope, null, null, values);
    }

    public ModerationDecision checkContent(Long uid, String scope, String sourceType, Long sourceId, String... values) {
        return checkContent(uid, scope, sourceType, sourceId, true, values);
    }

    public ModerationDecision checkNewSourceContent(Long uid, String scope, String sourceType, Long sourceId,
                                                    String... values) {
        return checkContent(uid, scope, sourceType, sourceId, false, values);
    }

    private ModerationDecision checkContent(Long uid, String scope, String sourceType, Long sourceId,
                                            boolean authorizeExistingSource, String... values) {
        String normalizedScope = normalizeScope(scope);
        String normalizedSourceType = normalizeSourceType(sourceType);
        if (authorizeExistingSource) {
            requireSourceAuthorized(uid, normalizedScope, normalizedSourceType, sourceId);
        }
        String text = values == null ? "" : String.join("\n", Arrays.stream(values)
                .filter(Objects::nonNull)
                .toList()).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return ModerationDecision.allow();
        }
        requireTableAvailable("t_moderation_keyword", "content keyword moderation");
        try {
            ModerationDecision reviewDecision = null;
            String contentSummary = summary(text);
            for (ModerationKeyword keyword : listEnabledKeywords(normalizedScope)) {
                if (matches(text, keyword)) {
                    String action = normalizeAction(keyword.getAction());
                    recordHit(uid, normalizedScope, normalizedSourceType, sourceId, keyword, action, text);
                    if ("BLOCK".equals(action)) {
                        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "内容包含不适合发布的词，请调整后再提交");
                    }
                    if (reviewDecision == null) {
                        reviewDecision = ModerationDecision.review(keyword.getKeyword(), contentSummary);
                    }
                }
            }
            return reviewDecision == null ? ModerationDecision.allow() : reviewDecision;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("content moderation keyword check failed closed: scope={}", scope, e);
            throw moderationUnavailable();
        }
    }

    private void requireSourceAuthorized(Long uid, String scope, String sourceType, Long sourceId) {
        if (sourceId == null || sourceId <= 0 || sourceAuthorizationHandlers == null) {
            return;
        }
        sourceAuthorizationHandlers.orderedStream()
                .filter(handler -> handler.supports(scope, sourceType))
                .forEach(handler -> handler.requireAuthorized(uid, sourceId));
    }

    private void recordHit(Long uid, String scope, String sourceType, Long sourceId,
                           ModerationKeyword keyword, String action, String text) {
        if (!tableExists("t_moderation_keyword_hit")) {
            log.warn("moderation keyword hit table unavailable, fail closed: scope={} uid={}", scope, LogMask.id(uid));
            throw moderationUnavailable();
        }
        try {
            ModerationKeywordHit hit = new ModerationKeywordHit();
            hit.setId(idGen.nextId());
            hit.setScope(scope);
            hit.setUid(uid);
            hit.setKeywordId(keyword.getId());
            hit.setKeyword(keyword.getKeyword());
            hit.setAction(action);
            hit.setContentSummary(summary(text));
            hit.setSourceType(sourceType);
            hit.setSourceId(sourceId);
            mapper.insertKeywordHit(hit);
            if ("REVIEW".equals(action)) {
                events.publishEvent(new ReviewQueueUpsertRequestedEvent(new ReviewQueueItemCommand(
                        "MODERATION_HIT",
                        hit.getId(),
                        "敏感词待审核：" + keyword.getKeyword(),
                        "范围：" + scope + " / 用户：" + uid + " / 内容：" + hit.getContentSummary(),
                        "medium",
                        uid,
                        60,
                        "{\"scope\":\"" + scope + "\",\"keywordId\":" + keyword.getId() + "}",
                        "moderation review hit"
                )));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("moderation keyword hit log failed closed: scope={} uid={}", scope, LogMask.id(uid), e);
            throw moderationUnavailable();
        }
    }

    private List<ModerationKeyword> listEnabledKeywords(String scope) {
        List<ModerationKeyword> keywords = new ArrayList<>();
        int offset = 0;
        while (true) {
            int limit = Math.min(KEYWORD_PAGE_SIZE, MAX_ENABLED_KEYWORDS + 1 - keywords.size());
            List<ModerationKeyword> page = mapper.listEnabledKeywords(scope, limit, offset);
            if (page == null || page.isEmpty()) {
                return keywords;
            }
            keywords.addAll(page);
            if (keywords.size() > MAX_ENABLED_KEYWORDS) {
                log.warn("too many enabled moderation keywords, fail closed: scope={} count>{}", scope, MAX_ENABLED_KEYWORDS);
                throw moderationUnavailable();
            }
            if (page.size() < limit) {
                return keywords;
            }
            offset += page.size();
        }
    }

    private String normalizeAction(String action) {
        return "REVIEW".equalsIgnoreCase(action) ? "REVIEW" : "BLOCK";
    }

    private String normalizeSourceType(String sourceType) {
        return StringUtils.hasText(sourceType) ? sourceType.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeReviewStatus(String reviewStatus) {
        String value = StringUtils.hasText(reviewStatus) ? reviewStatus.trim().toUpperCase(Locale.ROOT) : "CLOSED";
        return switch (value) {
            case "APPROVED", "REJECTED", "CLOSED" -> value;
            default -> "CLOSED";
        };
    }

    private String summary(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean matches(String text, ModerationKeyword keyword) {
        String word = keyword == null ? "" : keyword.getKeyword();
        if (!StringUtils.hasText(word)) {
            return false;
        }
        String normalized = word.trim().toLowerCase(Locale.ROOT);
        if ("EXACT".equalsIgnoreCase(keyword.getMatchType())) {
            return text.equals(normalized);
        }
        return text.contains(normalized);
    }

    private String normalizeScope(String scope) {
        return StringUtils.hasText(scope) ? scope.trim().toUpperCase(Locale.ROOT) : "ALL";
    }

    private boolean tableExists(String tableName) {
        try {
            return mapper.tableExists(tableName) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void requireTableAvailable(String tableName, String scene) {
        if (!tableExists(tableName)) {
            log.warn("content moderation dependency unavailable: scene={} table={}", scene, tableName);
            throw moderationUnavailable();
        }
    }

    private BizException moderationUnavailable() {
        return new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(), "内容安全服务暂时不可用，请稍后再试");
    }
}
