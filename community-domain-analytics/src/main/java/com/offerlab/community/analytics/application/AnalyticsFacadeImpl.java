package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.AnalyticsFacade;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MVP 占位
 */
@Service
@RequiredArgsConstructor
public class AnalyticsFacadeImpl implements AnalyticsFacade {

    private final PostMapper postMapper;
    private final TagMapper tagMapper;

    @Override
    public void track(Map<String, Object> event) {
        throw analyticsUnavailable("analytics event tracking is not available");
    }

    @Override
    public List<Map<String, Object>> getHotPosts(int size) {
        throw analyticsUnavailable("hot post analytics is not available");
    }

    @Override
    public Map<String, Object> getTrendDashboard(String range, Integer domain) {
        String normalizedRange = normalizeRange(range);
        Integer activeDomain = normalizeDomain(domain);
        int days = rangeDays(normalizedRange);
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        long total = postMapper.countPublishedSince(since, activeDomain);
        long allDomainTotal = postMapper.countPublishedSince(since, null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", normalizedRange);
        data.put("days", days);
        data.put("activeDomain", activeDomain);
        data.put("totalPosts", total);
        data.put("featuredPosts", postMapper.countFeaturedPostsSince(since, activeDomain));
        data.put("activeAuthors", postMapper.countActiveAuthorsSince(since, activeDomain));
        data.put("publishTrend", fillTrend(days, postMapper.countPublishedByDate(since, activeDomain)));
        data.put("topCompanies", postMapper.countCompanies(since, 10, activeDomain));
        data.put("topTags", tagMapper.countTopTags(since, 10, activeDomain));
        data.put("contentTypeDistribution", withPercentage(labelPostTypes(postMapper.countPostTypes(since, 10, activeDomain)), total));
        data.put("featuredContent", postMapper.listFeaturedContent(since, 8, activeDomain));
        data.put("positionDistribution", withPercentage(postMapper.countPositions(since, 10, activeDomain), total));
        data.put("resultDistribution", postMapper.countInterviewResults(since, 10, activeDomain));
        data.put("domainDistribution", withPercentage(labelDomains(postMapper.countDomainDistribution(since)), allDomainTotal));
        data.put("domainComparison", buildDomainComparison(since, allDomainTotal));
        data.put("domainHotContent", postMapper.listDomainHotContent(since, 8, activeDomain));
        return data;
    }

    @Override
    public Map<String, Object> getPersonalDashboard(Long uid) {
        throw analyticsUnavailable("personal analytics dashboard is not available");
    }

    private static BizException analyticsUnavailable(String message) {
        return new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(), message);
    }

    private static String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "30d";
        }
        return switch (range.trim()) {
            case "7d", "week" -> "7d";
            case "90d", "quarter" -> "90d";
            default -> "30d";
        };
    }

    private static int rangeDays(String range) {
        return switch (range) {
            case "7d" -> 7;
            case "90d" -> 90;
            default -> 30;
        };
    }

    private static Integer normalizeDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static List<Map<String, Object>> fillTrend(int days, List<Map<String, Object>> rows) {
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("label")),
                        row -> asLong(row.get("count")),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            LocalDate day = start.plusDays(i);
            result.add(Map.of(
                    "label", day.toString(),
                    "count", counts.getOrDefault(day.toString(), 0L)
            ));
        }
        return result;
    }

    private static List<Map<String, Object>> withPercentage(List<Map<String, Object>> rows, long total) {
        if (total <= 0) {
            return rows;
        }
        return rows.stream()
                .map(row -> {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    copy.put("percentage", Math.round(asLong(row.get("count")) * 100.0 / total));
                    return copy;
                })
                .toList();
    }

    private static List<Map<String, Object>> labelPostTypes(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    copy.put("name", postTypeName(row.get("name")));
                    return copy;
                })
                .toList();
    }

    private static List<Map<String, Object>> labelDomains(List<Map<String, Object>> rows) {
        return (rows == null ? List.<Map<String, Object>>of() : rows).stream()
                .filter(row -> row != null && PostDomain.isValid(asInteger(row.get("name"))))
                .map(row -> {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    copy.put("name", domainName(row.get("name")));
                    return copy;
                })
                .toList();
    }

    private List<Map<String, Object>> buildDomainComparison(LocalDateTime since, long allDomainTotal) {
        Map<Integer, Map<String, Object>> statsByDomain = byDomain(postMapper.listDomainComparisonStats(since));
        Map<Integer, List<Map<String, Object>>> topTagsByDomain = groupRowsByDomain(tagMapper.countTopTagsByDomain(since, 5));
        Map<Integer, List<Map<String, Object>>> hotContentByDomain = groupRowsByDomain(postMapper.listDomainHotContentByDomain(since, 5));
        List<Map<String, Object>> result = new ArrayList<>();
        for (PostDomain postDomain : PostDomain.values()) {
            Integer domainCode = postDomain.getCode();
            Map<String, Object> stats = statsByDomain.getOrDefault(domainCode, Map.of());
            long postCount = asLong(stats.get("postCount"));
            long featuredCount = asLong(stats.get("featuredCount"));
            long activeAuthors = asLong(stats.get("activeAuthors"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("domain", domainCode);
            item.put("name", postDomain.getDisplayName());
            item.put("postCount", postCount);
            item.put("featuredCount", featuredCount);
            item.put("activeAuthors", activeAuthors);
            item.put("share", allDomainTotal <= 0 ? 0 : Math.round(postCount * 100.0 / allDomainTotal));
            item.put("featuredRate", postCount <= 0 ? 0 : Math.round(featuredCount * 100.0 / postCount));
            item.put("topTags", topTagsByDomain.getOrDefault(domainCode, List.of()));
            item.put("hotContent", hotContentByDomain.getOrDefault(domainCode, List.of()));
            result.add(item);
        }
        return result;
    }

    private static Map<Integer, Map<String, Object>> byDomain(List<Map<String, Object>> rows) {
        Map<Integer, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            Integer domain = asInteger(row.get("domain"));
            if (PostDomain.isValid(domain)) {
                result.put(domain, row);
            }
        }
        return result;
    }

    private static Map<Integer, List<Map<String, Object>>> groupRowsByDomain(List<Map<String, Object>> rows) {
        Map<Integer, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
            Integer domain = asInteger(row.get("domain"));
            if (!PostDomain.isValid(domain)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.remove("domain");
            result.computeIfAbsent(domain, ignored -> new ArrayList<>()).add(copy);
        }
        return result;
    }

    private static String domainName(Object value) {
        Integer code = asInteger(value);
        return PostDomain.fromCode(code).getDisplayName();
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

    private static String postTypeName(Object value) {
        int type = value instanceof Number number ? number.intValue() : 0;
        return switch (type) {
            case 10 -> "攻略清单";
            case 11 -> "复盘记录";
            case 12 -> "图文笔记";
            case 13 -> "问题求助";
            case 14 -> "资源推荐";
            case 15 -> "经验分享";
            case 16 -> "观点讨论";
            case 1 -> "历史经验";
            case 2 -> "历史博客";
            case 3 -> "历史题解";
            case 4 -> "历史问答";
            default -> "其他内容";
        };
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
