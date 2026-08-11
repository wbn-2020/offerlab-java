package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.GrowthProfileDTO;
import com.offerlab.community.analytics.api.dto.GrowthReportDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthEventMapper;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthInsightMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.ContentSeriesPostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.ContentSeriesPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GrowthInsightService {

    private final GrowthInsightMapper growthInsightMapper;
    private final GrowthEventMapper growthEventMapper;
    private final TagMapper tagMapper;
    private final ContentSeriesMapper contentSeriesMapper;
    private final ContentSeriesPostMapper contentSeriesPostMapper;

    public GrowthProfileDTO profile(Long uid, int days) {
        requireUser(uid);
        int safeDays = normalizeDays(days);
        LocalDateTime since = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        boolean growthEventReady = growthEventReady();
        boolean seriesReady = seriesReady();
        boolean crossDomainConsumeReady = false;
        Map<Integer, Long> seriesCountByDomain = seriesReady ? countSeriesByDomain(uid) : Map.of();
        Map<Integer, List<GrowthProfileDTO.ReferencePostDTO>> postsByDomain = toReferencePosts(
                growthInsightMapper.selectRepresentativePosts(uid, since, 4)).stream()
                .filter(item -> item.getDomain() != null)
                .collect(Collectors.groupingBy(
                        GrowthProfileDTO.ReferencePostDTO::getDomain,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<GrowthProfileDTO.DomainProfileDTO> domains = buildDomainProfiles(
                growthInsightMapper.selectAuthorDomainStats(uid, since),
                seriesCountByDomain,
                postsByDomain);
        List<String> degradationReasons = profileDegradationReasons(growthEventReady, seriesReady, crossDomainConsumeReady);
        GrowthProfileDTO.DomainProfileDTO strongest = domains.isEmpty() ? null : domains.get(0);
        GrowthProfileDTO.DomainProfileDTO emerging = domains.stream()
                .filter(item -> strongest == null || !Objects.equals(item.getDomain(), strongest.getDomain()))
                .findFirst()
                .orElse(null);
        return GrowthProfileDTO.builder()
                .days(safeDays)
                .degraded(!degradationReasons.isEmpty())
                .degradationReasons(degradationReasons)
                .strongestDomain(strongest == null ? null : strongest.getDomainName())
                .emergingDomain(emerging == null ? null : emerging.getDomainName())
                .nextFocus(buildNextFocus(domains, seriesReady, since))
                .domains(domains)
                .build();
    }

    public GrowthReportDTO report(Long uid, String period) {
        requireUser(uid);
        String normalizedPeriod = normalizePeriod(period);
        int days = "monthly".equals(normalizedPeriod) ? 30 : 7;
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        LocalDateTime previousSince = since.minusDays(days);
        boolean growthEventReady = growthEventReady();
        boolean seriesReady = seriesReady();

        List<Map<String, Object>> currentRows = safeRows(growthInsightMapper.selectAuthorDomainStats(uid, since));
        List<Map<String, Object>> previousRows = safeRows(growthInsightMapper.selectPreviousAuthorDomainStats(uid, previousSince, since));
        Map<Integer, Long> seriesCountByDomain = seriesReady ? countSeriesByDomain(uid) : Map.of();
        List<String> degradationReasons = baseDegradationReasons(growthEventReady, seriesReady);

        return GrowthReportDTO.builder()
                .period(normalizedPeriod)
                .days(days)
                .degraded(!degradationReasons.isEmpty())
                .degradationReasons(degradationReasons)
                .publishedPostCount(currentRows.stream().mapToLong(row -> asLong(row.get("postCount"))).sum())
                .interactionCount(currentRows.stream().mapToLong(this::interactionCount).sum())
                .featuredPostCount(currentRows.stream().mapToLong(row -> asLong(row.get("featuredCount"))).sum())
                .seriesContributionCount(seriesCountByDomain.values().stream().mapToLong(Long::longValue).sum())
                .domainChanges(buildDomainChanges(currentRows, previousRows))
                .highlightPosts(toHighlightPosts(growthInsightMapper.selectRepresentativePosts(uid, since, 3)))
                .nextActions(buildNextActions(currentRows, seriesCountByDomain, since))
                .build();
    }

    private List<GrowthProfileDTO.DomainProfileDTO> buildDomainProfiles(List<Map<String, Object>> rows,
                                                                        Map<Integer, Long> seriesCountByDomain,
                                                                        Map<Integer, List<GrowthProfileDTO.ReferencePostDTO>> postsByDomain) {
        List<GrowthProfileDTO.DomainProfileDTO> result = new ArrayList<>();
        for (Map<String, Object> row : safeRows(rows)) {
            Integer domain = asInteger(row.get("domain"));
            if (domain == null) {
                continue;
            }
            long postCount = asLong(row.get("postCount"));
            long featuredCount = asLong(row.get("featuredCount"));
            long activeDays = asLong(row.get("activeDays"));
            long viewCount = asLong(row.get("viewCount"));
            long interactionCount = interactionCount(row);
            long seriesCount = seriesCountByDomain.getOrDefault(domain, 0L);
            long avgContentLength = asLong(row.get("avgContentLength"));
            int activityScore = clampScore(postCount * 14 + activeDays * 9);
            int responseScore = clampScore(interactionCount * 3 + featuredCount * 12 + Math.round(viewCount * 0.15));
            int depthScore = clampScore(featuredCount * 15 + seriesCount * 18 + Math.round(avgContentLength / 40D));
            int consistencyScore = clampScore(activeDays * 15 + (postCount >= 3 ? 10 : 0) + (seriesCount > 0 ? 10 : 0));
            result.add(GrowthProfileDTO.DomainProfileDTO.builder()
                    .domain(domain)
                    .domainName(PostDomain.fromCode(domain).getDisplayName())
                    .postCount(postCount)
                    .seriesCount(seriesCount)
                    .activeDays(activeDays)
                    .interactionCount(interactionCount)
                    .viewCount(viewCount)
                    .dimensions(List.of(
                            dimension("activity", "Activity", activityScore,
                                    "Published " + postCount + " posts across " + activeDays + " active days"),
                            dimension("visible_feedback", "Visible feedback", responseScore,
                                    "Captured " + interactionCount + " visible interactions, " + viewCount + " views, and " + featuredCount + " featured posts"),
                            dimension("depth", "Depth", depthScore,
                                    "Average content length is about " + avgContentLength + " chars with " + seriesCount + " series links"),
                            dimension("consistency", "Consistency", consistencyScore,
                                    "Maintained " + activeDays + " active days and " + seriesCount + " series anchors")))
                    .representativePosts(safeList(postsByDomain.get(domain)).stream().limit(2).toList())
                    .build());
        }
        result.sort(Comparator
                .comparingInt((GrowthProfileDTO.DomainProfileDTO item) -> totalDimensionScore(item.getDimensions()))
                .reversed()
                .thenComparing(GrowthProfileDTO.DomainProfileDTO::getPostCount, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private List<GrowthReportDTO.DomainChangeDTO> buildDomainChanges(List<Map<String, Object>> currentRows,
                                                                     List<Map<String, Object>> previousRows) {
        Map<Integer, Long> previousCounts = safeRows(previousRows).stream()
                .collect(Collectors.toMap(
                        row -> asInteger(row.get("domain")),
                        row -> asLong(row.get("postCount")),
                        Long::sum,
                        LinkedHashMap::new));
        List<GrowthReportDTO.DomainChangeDTO> changes = new ArrayList<>();
        for (Map<String, Object> row : safeRows(currentRows)) {
            Integer domain = asInteger(row.get("domain"));
            if (domain == null) {
                continue;
            }
            long currentPostCount = asLong(row.get("postCount"));
            long previousPostCount = previousCounts.getOrDefault(domain, 0L);
            changes.add(GrowthReportDTO.DomainChangeDTO.builder()
                    .domain(domain)
                    .domainName(PostDomain.fromCode(domain).getDisplayName())
                    .currentPostCount(currentPostCount)
                    .previousPostCount(previousPostCount)
                    .trend(trend(currentPostCount, previousPostCount))
                    .reason(trendReason(domain, currentPostCount, previousPostCount))
                    .build());
        }
        changes.sort(Comparator
                .comparing(GrowthReportDTO.DomainChangeDTO::getCurrentPostCount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GrowthReportDTO.DomainChangeDTO::getDomain, Comparator.nullsLast(Comparator.naturalOrder())));
        return changes;
    }

    private List<GrowthProfileDTO.ReferencePostDTO> toReferencePosts(List<Map<String, Object>> rows) {
        return safeRows(rows).stream()
                .map(row -> GrowthProfileDTO.ReferencePostDTO.builder()
                        .postId(asLongObject(row.get("postId")))
                        .title(String.valueOf(row.getOrDefault("title", "")))
                        .domain(asInteger(row.get("domain")))
                        .heat(asLong(row.get("interactionCount")))
                        .featured(asLong(row.get("featured")) > 0)
                        .build())
                .toList();
    }

    private List<GrowthReportDTO.HighlightPostDTO> toHighlightPosts(List<Map<String, Object>> rows) {
        return safeRows(rows).stream()
                .map(row -> {
                    Integer domain = asInteger(row.get("domain"));
                    return GrowthReportDTO.HighlightPostDTO.builder()
                            .postId(asLongObject(row.get("postId")))
                            .title(String.valueOf(row.getOrDefault("title", "")))
                            .domain(domain)
                            .domainName(PostDomain.fromCode(domain).getDisplayName())
                            .interactionCount(asLong(row.get("interactionCount")))
                            .featured(asLong(row.get("featured")) > 0)
                            .build();
                })
                .toList();
    }

    private String buildNextFocus(List<GrowthProfileDTO.DomainProfileDTO> domains, boolean seriesReady, LocalDateTime since) {
        List<String> actions = buildNextActions(
                domains.stream().map(this::domainToRow).toList(),
                domains.stream().collect(Collectors.toMap(
                        GrowthProfileDTO.DomainProfileDTO::getDomain,
                        item -> item.getSeriesCount() == null ? 0L : item.getSeriesCount(),
                        Long::sum,
                        LinkedHashMap::new)),
                since);
        if (actions.isEmpty()) {
            return "Keep a stable publishing rhythm and turn high-response content into reusable assets.";
        }
        if (!seriesReady) {
            return actions.get(0) + " (series metrics are currently degraded by local schema state)";
        }
        return actions.get(0);
    }

    private List<String> buildNextActions(List<Map<String, Object>> currentRows,
                                          Map<Integer, Long> seriesCountByDomain,
                                          LocalDateTime since) {
        if (currentRows == null || currentRows.isEmpty()) {
            return List.of("先发布一篇结构化内容，让成长雷达和报告积累可用信号。");
        }
        List<String> actions = new ArrayList<>();
        Map<String, Object> strongest = currentRows.stream()
                .max(Comparator.comparingLong(row -> asLong(row.get("postCount"))))
                .orElse(null);
        Integer strongestDomain = strongest == null ? null : asInteger(strongest.get("domain"));
        long totalPosts = currentRows.stream().mapToLong(row -> asLong(row.get("postCount"))).sum();
        if (currentRows.size() == 1 && strongestDomain != null) {
            actions.add("Bridge your " + PostDomain.fromCode(strongestDomain).getDisplayName()
                    + " experience into another domain as a follow-up direction readers can evaluate in context.");
        }
        if (strongestDomain != null && seriesCountByDomain.getOrDefault(strongestDomain, 0L) == 0L && totalPosts >= 2) {
            actions.add("Group your strongest " + PostDomain.fromCode(strongestDomain).getDisplayName()
                    + " topic into a content series to improve continuity and reuse.");
        }
        if (strongest != null && asLong(strongest.get("avgContentLength")) < 900L) {
            actions.add("Add more process detail, results, and boundaries in the next post to raise depth.");
        }
        List<Map<String, Object>> topTags = strongestDomain == null ? List.of() : safeRows(tagMapper.countTopTags(since, 3, strongestDomain));
        if (!topTags.isEmpty()) {
            actions.add("Continue building around " + String.valueOf(topTags.get(0).get("name"))
                    + " to connect with current community demand.");
        }
        if (actions.isEmpty()) {
            actions.add("Keep the current output rhythm and revisit the topics that drew the highest interaction.");
        }
        return actions.stream().filter(StringUtils::hasText).distinct().limit(3).toList();
    }

    private Map<Integer, Long> countEventByDomain(Long uid, LocalDateTime since, String eventType) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : safeRows(growthEventMapper.countUserEventsByDomain(uid, since))) {
            if (!Objects.equals(String.valueOf(row.get("eventType")), eventType)) {
                continue;
            }
            Integer domain = asInteger(row.get("domain"));
            if (domain != null) {
                result.put(domain, result.getOrDefault(domain, 0L) + asLong(row.get("count")));
            }
        }
        return result;
    }

    private Map<Integer, Long> countSeriesByDomain(Long uid) {
        List<ContentSeriesPO> series = contentSeriesMapper.selectMine(uid);
        if (series == null || series.isEmpty()) {
            return Map.of();
        }
        return series.stream()
                .filter(item -> item.getDomain() != null)
                .collect(Collectors.groupingBy(ContentSeriesPO::getDomain, LinkedHashMap::new, Collectors.counting()));
    }

    private boolean growthEventReady() {
        try {
            return growthEventMapper.tableExists() > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean seriesReady() {
        try {
            return contentSeriesMapper.tableExists() > 0 && contentSeriesPostMapper.tableExists() > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static GrowthProfileDTO.DimensionDTO dimension(String key, String label, int score, String explanation) {
        return GrowthProfileDTO.DimensionDTO.builder()
                .key(key)
                .label(label)
                .score(score)
                .explanation(explanation)
                .build();
    }

    private Map<String, Object> domainToRow(GrowthProfileDTO.DomainProfileDTO item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("domain", item.getDomain());
        row.put("postCount", item.getPostCount());
        row.put("avgContentLength", 1000L);
        return row;
    }

    private static List<String> profileDegradationReasons(boolean growthEventReady,
                                                          boolean seriesReady,
                                                          boolean crossDomainConsumeReady) {
        List<String> reasons = baseDegradationReasons(growthEventReady, seriesReady);
        if (!crossDomainConsumeReady) {
            reasons.add("cross-domain consume metrics fallback: public feedback event chain not calibrated");
        }
        return reasons;
    }

    private static List<String> baseDegradationReasons(boolean growthEventReady, boolean seriesReady) {
        List<String> reasons = new ArrayList<>();
        if (!growthEventReady) {
            reasons.add("growth event metrics fallback: local schema not ready");
        }
        if (!seriesReady) {
            reasons.add("content series metrics fallback: local schema not ready");
        }
        return reasons;
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static int normalizeDays(int days) {
        return Math.max(7, Math.min(days <= 0 ? 30 : days, 90));
    }

    private static String normalizePeriod(String period) {
        if (!StringUtils.hasText(period)) {
            return "weekly";
        }
        return "monthly".equalsIgnoreCase(period.trim()) ? "monthly" : "weekly";
    }

    private long interactionCount(Map<String, Object> row) {
        return asLong(row.get("likeCount")) + asLong(row.get("favoriteCount")) + asLong(row.get("commentCount"));
    }

    private static String trend(long current, long previous) {
        if (current > 0 && previous == 0) {
            return "new";
        }
        if (current > previous) {
            return "up";
        }
        if (current < previous) {
            return "down";
        }
        return "steady";
    }

    private static String trendReason(Integer domain, long current, long previous) {
        String domainName = PostDomain.fromCode(domain).getDisplayName();
        if (current > 0 && previous == 0) {
            return "This period started a stable output pattern in " + domainName + ".";
        }
        if (current > previous) {
            return "Output in " + domainName + " increased versus the previous window.";
        }
        if (current < previous) {
            return "Output in " + domainName + " slowed down and may need a high-response follow-up.";
        }
        return "Output in " + domainName + " stayed close to the previous window.";
    }

    private static int totalDimensionScore(Collection<GrowthProfileDTO.DimensionDTO> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return 0;
        }
        return dimensions.stream().mapToInt(GrowthProfileDTO.DimensionDTO::getScore).sum();
    }

    private static int clampScore(double value) {
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }

    private static List<Map<String, Object>> safeRows(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private static <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Long asLongObject(Object value) {
        if (value == null) {
            return null;
        }
        long number = asLong(value);
        return number <= 0 ? null : number;
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
}
