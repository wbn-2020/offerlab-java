package com.offerlab.community.infra.moderation;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
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

    private final ContentModerationMapper mapper;

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
        if (!tableExists("t_moderation_keyword")) {
            return;
        }
        String text = values == null ? "" : String.join("\n", Arrays.stream(values)
                .filter(Objects::nonNull)
                .toList()).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return;
        }
        try {
            for (ModerationKeyword keyword : mapper.listEnabledKeywords(normalizeScope(scope))) {
                if (matches(text, keyword)) {
                    throw new BizException(ErrorCode.PARAM_ERROR);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("content moderation keyword check failed open: scope={}", scope, e);
        }
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
