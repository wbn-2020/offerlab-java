package com.offerlab.community.infra.moderation;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModerationAdminService {
    private final ContentModerationMapper mapper;
    private final SnowflakeIdGenerator idGen;

    public List<ModerationKeyword> listKeywords(String keyword, String scope, int limit) {
        ensureTable("t_moderation_keyword");
        return mapper.listKeywords(clean(keyword), clean(scope), Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100)));
    }

    public List<ModerationKeywordHit> listKeywordHits(String scope, String action, Long uid, String keyword, int limit) {
        ensureTable("t_moderation_keyword_hit");
        return mapper.listKeywordHits(clean(scope), clean(action), uid, clean(keyword), Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100)));
    }

    @Transactional
    public ModerationKeyword saveKeyword(Long id, ModerationKeywordAdminCmd cmd, Long operatorUid) {
        ensureTable("t_moderation_keyword");
        String keyword = clean(cmd == null ? null : cmd.getKeyword());
        if (keyword == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (keyword.length() < 2 || keyword.length() > 128) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ModerationKeyword po = new ModerationKeyword();
        po.setId(id == null ? idGen.nextId() : id);
        po.setKeyword(keyword);
        po.setMatchType(normalize(cmd == null ? null : cmd.getMatchType(), List.of("CONTAINS", "EXACT"), "CONTAINS"));
        po.setAction(normalize(cmd == null ? null : cmd.getAction(), List.of("BLOCK", "REVIEW"), "BLOCK"));
        po.setScope(normalize(cmd == null ? null : cmd.getScope(), List.of("ALL", "POST", "COMMENT", "REPORT"), "ALL"));
        po.setEnabled(cmd == null || cmd.getEnabled() == null || cmd.getEnabled() == 1 ? 1 : 0);
        po.setRemark(limit(cmd == null ? null : cmd.getRemark(), 200));
        po.setOperatorUid(operatorUid);
        if (id == null) {
            mapper.insertKeyword(po);
        } else if (mapper.updateKeyword(po) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return mapper.findKeywordById(po.getId());
    }

    @Transactional
    public Map<String, Object> updateKeywordStatus(Long id, int enabled, Long operatorUid) {
        ensureTable("t_moderation_keyword");
        int value = enabled == 1 ? 1 : 0;
        if (mapper.updateKeywordStatus(id, value, operatorUid) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return Map.of("id", id, "enabled", value);
    }

    public List<UserModerationState> listUserStates(int limit) {
        ensureTable("t_user_moderation_state");
        List<UserModerationState> states = mapper.listUserStates(Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100)));
        enrichRecentViolations(states);
        return states;
    }

    @Transactional
    public UserModerationState saveUserState(UserModerationStateCmd cmd, Long operatorUid) {
        ensureTable("t_user_moderation_state");
        if (cmd == null || cmd.getUid() == null || cmd.getUid() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime mutedUntil = hoursFromNow(now, cmd.getMuteHours());
        LocalDateTime bannedUntil = hoursFromNow(now, cmd.getBanHours());
        mapper.upsertUserState(cmd.getUid(), mutedUntil, bannedUntil, limit(cmd.getReason(), 500), operatorUid);
        UserModerationState state = mapper.findUserState(cmd.getUid());
        if (state == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        enrichRecentViolations(List.of(state));
        return state;
    }

    @Transactional
    public UserModerationState clearUserMute(Long uid, Long operatorUid) {
        ensureTable("t_user_moderation_state");
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (mapper.clearMute(uid, "解除禁言", operatorUid) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        UserModerationState state = mapper.findUserState(uid);
        if (state == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        enrichRecentViolations(List.of(state));
        return state;
    }

    @Transactional
    public UserModerationState clearUserBan(Long uid, Long operatorUid) {
        ensureTable("t_user_moderation_state");
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (mapper.clearBan(uid, "解除封禁", operatorUid) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        UserModerationState state = mapper.findUserState(uid);
        if (state == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        enrichRecentViolations(List.of(state));
        return state;
    }

    private void enrichRecentViolations(List<UserModerationState> states) {
        if (states == null || states.isEmpty() || mapper.tableExists("t_moderation_keyword_hit") <= 0) {
            return;
        }
        List<Long> uids = states.stream()
                .filter(Objects::nonNull)
                .map(UserModerationState::getUid)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (uids.isEmpty()) {
            return;
        }
        Map<Long, UserModerationState> latest = mapper.listLatestUserViolations(uids).stream()
                .filter(item -> item.getUid() != null)
                .collect(Collectors.toMap(UserModerationState::getUid, item -> item, (a, b) -> a));
        states.stream().filter(Objects::nonNull).forEach(state -> {
            UserModerationState hit = latest.get(state.getUid());
            if (hit != null) {
                state.setRecentViolationKeyword(hit.getRecentViolationKeyword());
                state.setRecentViolationAction(hit.getRecentViolationAction());
                state.setRecentViolationSummary(hit.getRecentViolationSummary());
                state.setRecentViolationTime(hit.getRecentViolationTime());
            }
        });
    }

    private LocalDateTime hoursFromNow(LocalDateTime now, Integer hours) {
        if (hours == null || hours <= 0) {
            return null;
        }
        return now.plusHours(Math.min(hours, 24 * 365));
    }

    private void ensureTable(String tableName) {
        if (mapper.tableExists(tableName) <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private String normalize(String value, List<String> allowed, String fallback) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback;
        if (!allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
