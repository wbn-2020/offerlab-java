package com.offerlab.community.infra.moderation;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentModerationService {
    public static final String SCOPE_POST = "POST";
    public static final String SCOPE_COMMENT = "COMMENT";
    public static final String SCOPE_REPORT = "REPORT";

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

    public void requireUserCanPublish(Long uid) {
        if (uid == null || !tableExists("t_user_moderation_state")) {
            return;
        }
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
            log.warn("user moderation check failed open: uid={}", uid, e);
        }
    }

    public void requireContentAllowed(String scope, String... values) {
        requireContentAllowed(null, scope, values);
    }

    public void requireContentAllowed(Long uid, String scope, String... values) {
        checkContent(uid, scope, values);
    }

    public ModerationDecision checkContent(Long uid, String scope, String... values) {
        if (!tableExists("t_moderation_keyword")) {
            return ModerationDecision.allow();
        }
        String text = values == null ? "" : String.join("\n", Arrays.stream(values)
                .filter(Objects::nonNull)
                .toList()).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return ModerationDecision.allow();
        }
        try {
            String normalizedScope = normalizeScope(scope);
            ModerationDecision reviewDecision = null;
            String contentSummary = summary(text);
            for (ModerationKeyword keyword : mapper.listEnabledKeywords(normalizedScope)) {
                if (matches(text, keyword)) {
                    String action = normalizeAction(keyword.getAction());
                    recordHit(uid, normalizedScope, keyword, action, text);
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
            log.warn("content moderation keyword check failed open: scope={}", scope, e);
            return ModerationDecision.allow();
        }
    }

    private void recordHit(Long uid, String scope, ModerationKeyword keyword, String action, String text) {
        if (!tableExists("t_moderation_keyword_hit")) {
            return;
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
            mapper.insertKeywordHit(hit);
        } catch (Exception e) {
            log.warn("moderation keyword hit log failed open: scope={} uid={}", scope, uid, e);
        }
    }

    private String normalizeAction(String action) {
        return "REVIEW".equalsIgnoreCase(action) ? "REVIEW" : "BLOCK";
    }

    private String summary(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return value.length() <= 200 ? value : value.substring(0, 200);
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
}
