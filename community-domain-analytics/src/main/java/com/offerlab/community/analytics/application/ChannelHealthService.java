package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ChannelHealthDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChannelHealthService {

    private final GrowthInsightMapper growthInsightMapper;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final MigrationCheckService migrationCheckService;

    public List<ChannelHealthDTO> list(Integer domain, Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!migrationCheckService.trustedContentReady()
                || !migrationCheckService.trustedDistributionReady()
                || !migrationCheckService.stageTwoToFiveReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Channel health requires the trusted distribution migration");
        }
        if (isGlobalModerator(uid)) {
            return query(domain);
        }
        List<Integer> moderatedDomains = domainModeratorService.listModeratedDomains(uid);
        if (domain != null) {
            if (!moderatedDomains.contains(domain)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            return query(domain);
        }
        if (moderatedDomains.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return moderatedDomains.stream()
                .sorted()
                .flatMap(item -> query(item).stream())
                .toList();
    }

    private List<ChannelHealthDTO> query(Integer domain) {
        return growthInsightMapper.selectChannelHealth(domain, Post.TYPE_COMMUNITY_QUESTION).stream()
                .map(this::toDto)
                .toList();
    }

    private boolean isGlobalModerator(Long uid) {
        return adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode();
    }

    private ChannelHealthDTO toDto(Map<String, Object> row) {
        Integer domain = integer(row.get("domain"));
        long publicPosts = nonNegative(row.get("publicPostCount"));
        long trustProfiles = nonNegative(row.get("trustProfileCount"));
        long freshness = nonNegative(row.get("freshnessAwaitingConfirmation"));
        long suggestions = nonNegative(row.get("pendingSuggestions"));
        long questions = nonNegative(row.get("unresolvedQuestions"));
        long needs = nonNegative(row.get("openContentNeeds"));
        int coverage = publicPosts == 0 ? 0 : (int) Math.min(100, trustProfiles * 100 / publicPosts);
        List<String> reasons = new ArrayList<>();
        if (publicPosts > 0 && coverage < 40) reasons.add("可信经验背景覆盖率低于 40%");
        if (freshness > 0) reasons.add("存在等待作者确认时效的内容");
        if (suggestions > 0) reasons.add("存在待处理的补充或纠错建议");
        if (questions > 0) reasons.add("存在尚未闭环的问题");
        if (needs > 0) reasons.add("存在待交付的公开内容需求");
        return ChannelHealthDTO.builder()
                .domain(domain)
                .domainName(domainName(domain))
                .publicPostCount(publicPosts)
                .trustProfileCount(trustProfiles)
                .trustProfileCoveragePercent(coverage)
                .freshnessAwaitingConfirmation(freshness)
                .pendingSuggestions(suggestions)
                .unresolvedQuestions(questions)
                .openContentNeeds(needs)
                .healthStatus(reasons.isEmpty() ? "STABLE" : "ATTENTION")
                .attentionReasons(reasons)
                .build();
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long nonNegative(Object value) {
        if (value instanceof Number number) return Math.max(0, number.longValue());
        try {
            return value == null ? 0 : Math.max(0, Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String domainName(Integer domain) {
        return switch (domain == null ? 0 : domain) {
            case 1 -> "科技数码";
            case 2 -> "职场经验";
            case 3 -> "阅读成长";
            case 4 -> "生活方式";
            case 5 -> "投资理财";
            default -> "未分类";
        };
    }
}
