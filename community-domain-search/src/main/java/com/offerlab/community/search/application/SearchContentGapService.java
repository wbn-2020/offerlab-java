package com.offerlab.community.search.application;

import com.offerlab.community.search.api.dto.SearchContentGapDTO;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchAnalyticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchContentGapService {

    static final int MIN_SAMPLE_COUNT = 5;
    private static final int WEAK_RESULT_THRESHOLD = 2;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(?:\\+?\\d[\\d\\s().-]{7,}\\d)$");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)^(?:https?://|www\\.)\\S+$");
    private static final Pattern JWT_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{8,}$");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{24,}$");

    private final SearchAnalyticsMapper mapper;

    public List<SearchContentGapDTO> candidates(int days, int limit) {
        return candidates(days, limit, false);
    }

    public List<SearchContentGapDTO> candidates(int days, int limit, boolean includeTestData) {
        if (!tableReady()) {
            return List.of();
        }
        int safeDays = Math.max(1, Math.min(days, 90));
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Map<String, GapStats> statsByKeyword = new LinkedHashMap<>();
        mapper.topSearchKeywords(safeDays, safeLimit, includeTestData)
                .forEach(row -> merge(statsByKeyword, row, SearchContentGapDTO.CreatedFrom.hot_keyword));
        mapper.topNoResultKeywords(safeDays, safeLimit, includeTestData)
                .forEach(row -> merge(statsByKeyword, row, SearchContentGapDTO.CreatedFrom.no_result));
        return statsByKeyword.values().stream()
                .map(stats -> toGap(stats, safeDays))
                .filter(gap -> gap.getKeyword() != null)
                .limit(safeLimit)
                .toList();
    }

    private boolean tableReady() {
        try {
            return mapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.debug("search content gap table check failed: {}", e.getMessage());
            return false;
        }
    }

    public List<SearchContentGapDTO> downstreamApprovedGaps(int days, int limit) {
        return candidates(days, limit, false).stream()
                .filter(gap -> Boolean.TRUE.equals(gap.getMinSampleMet()))
                .filter(gap -> SearchContentGapDTO.GapStatus.APPROVED.equals(gap.getStatus()))
                .filter(gap -> !SearchContentGapDTO.RiskLevel.HIGH.equals(gap.getRiskLevel()))
                .filter(gap -> !"fallback".equalsIgnoreCase(gap.getSource()))
                .filter(gap -> !"demo".equalsIgnoreCase(gap.getSource()))
                .toList();
    }

    private void merge(Map<String, GapStats> statsByKeyword, Map<String, Object> row,
                       SearchContentGapDTO.CreatedFrom createdFrom) {
        String keyword = cleanKeyword(asText(row.get("keyword")));
        if (keyword == null) {
            return;
        }
        GapStats stats = statsByKeyword.computeIfAbsent(keyword, GapStats::new);
        stats.searchCount = Math.max(stats.searchCount, asLong(row.get("count")));
        stats.noResultCount = Math.max(stats.noResultCount, asLong(row.get("noResultCount")));
        long lastResultCount = asLong(row.get("lastResultCount"));
        if (lastResultCount > 0 && lastResultCount <= WEAK_RESULT_THRESHOLD) {
            stats.weakResultCount = Math.max(stats.weakResultCount, stats.searchCount - stats.noResultCount);
            stats.createdFrom = SearchContentGapDTO.CreatedFrom.weak_result;
        } else if (SearchContentGapDTO.CreatedFrom.no_result.equals(createdFrom)) {
            stats.createdFrom = SearchContentGapDTO.CreatedFrom.no_result;
        } else if (stats.createdFrom == null) {
            stats.createdFrom = createdFrom;
        }
        stats.lastSeenAt = latest(stats.lastSeenAt, asText(row.get("lastSearchedAt")));
    }

    private SearchContentGapDTO toGap(GapStats stats, int days) {
        long totalSignals = stats.searchCount + stats.noResultCount + stats.weakResultCount;
        boolean minSampleMet = totalSignals >= MIN_SAMPLE_COUNT;
        SearchContentGapDTO.RiskLevel riskLevel = riskLevel(stats.keyword, minSampleMet);
        SearchContentGapDTO.GapStatus status = status(minSampleMet, riskLevel);
        SearchContentGapDTO.CreatedFrom createdFrom = stats.createdFrom == null
                ? SearchContentGapDTO.CreatedFrom.hot_keyword
                : stats.createdFrom;
        return SearchContentGapDTO.builder()
                .gapId("search-gap-" + stableHash(stats.keyword))
                .keyword(stats.keyword)
                .clusterId("search-cluster-" + stableHash(clusterKey(stats.keyword)))
                .reasonText(reasonText(createdFrom, stats))
                .windowDays(days)
                .searchCount(stats.searchCount)
                .noResultCount(stats.noResultCount)
                .weakResultCount(stats.weakResultCount)
                .minSampleMet(minSampleMet)
                .riskLevel(riskLevel)
                .targetStage(targetStage(createdFrom))
                .status(status)
                .source("search")
                .sourceRefs(List.of("analytics:search:" + stableHash(stats.keyword)))
                .createdFrom(createdFrom)
                .lastSeenAt(stats.lastSeenAt)
                .build();
    }

    private String cleanKeyword(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isBlank() || text.length() > 80) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("fallback") || lower.contains("demo")) {
            return null;
        }
        if (looksSensitive(text) || looksPrivateTraining(text)) {
            return null;
        }
        return text;
    }

    private static boolean looksSensitive(String text) {
        String compact = text.replace(" ", "");
        return EMAIL_PATTERN.matcher(text).find()
                || PHONE_PATTERN.matcher(text).find()
                || URL_PATTERN.matcher(text).find()
                || JWT_PATTERN.matcher(text).find()
                || TOKEN_PATTERN.matcher(compact).find();
    }

    private static boolean looksPrivateTraining(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("my resume")
                || lower.contains("my interview")
                || lower.contains("private")
                || lower.contains("个人简历")
                || lower.contains("我的简历")
                || lower.contains("我的面试")
                || lower.contains("私人");
    }

    private static SearchContentGapDTO.RiskLevel riskLevel(String keyword, boolean minSampleMet) {
        if (!minSampleMet) {
            return SearchContentGapDTO.RiskLevel.MEDIUM;
        }
        return looksPrivateTraining(keyword) ? SearchContentGapDTO.RiskLevel.HIGH : SearchContentGapDTO.RiskLevel.LOW;
    }

    private static SearchContentGapDTO.GapStatus status(boolean minSampleMet, SearchContentGapDTO.RiskLevel riskLevel) {
        if (SearchContentGapDTO.RiskLevel.HIGH.equals(riskLevel)) {
            return SearchContentGapDTO.GapStatus.REVIEW_REQUIRED;
        }
        return minSampleMet ? SearchContentGapDTO.GapStatus.CANDIDATE : SearchContentGapDTO.GapStatus.REVIEW_REQUIRED;
    }

    private static SearchContentGapDTO.TargetStage targetStage(SearchContentGapDTO.CreatedFrom createdFrom) {
        if (SearchContentGapDTO.CreatedFrom.no_result.equals(createdFrom)) {
            return SearchContentGapDTO.TargetStage.workspace;
        }
        if (SearchContentGapDTO.CreatedFrom.weak_result.equals(createdFrom)) {
            return SearchContentGapDTO.TargetStage.editor;
        }
        if (SearchContentGapDTO.CreatedFrom.manual_review.equals(createdFrom)) {
            return SearchContentGapDTO.TargetStage.topic;
        }
        return SearchContentGapDTO.TargetStage.knowledge;
    }

    private static String reasonText(SearchContentGapDTO.CreatedFrom createdFrom, GapStats stats) {
        if (SearchContentGapDTO.CreatedFrom.no_result.equals(createdFrom)) {
            return "aggregated_no_result_searches";
        }
        if (SearchContentGapDTO.CreatedFrom.weak_result.equals(createdFrom)) {
            return "aggregated_weak_result_searches";
        }
        return stats.noResultCount > 0 ? "aggregated_search_demand_with_gaps" : "aggregated_hot_keyword";
    }

    private static String clusterKey(String keyword) {
        return keyword.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String latest(String oldValue, String newValue) {
        if (oldValue == null || oldValue.isBlank()) {
            return newValue;
        }
        if (newValue == null || newValue.isBlank()) {
            return oldValue;
        }
        try {
            return LocalDateTime.parse(newValue).isAfter(LocalDateTime.parse(oldValue)) ? newValue : oldValue;
        } catch (RuntimeException ignored) {
            return newValue.compareTo(oldValue) >= 0 ? newValue : oldValue;
        }
    }

    private static String stableHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
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
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static final class GapStats {
        private final String keyword;
        private long searchCount;
        private long noResultCount;
        private long weakResultCount;
        private SearchContentGapDTO.CreatedFrom createdFrom;
        private String lastSeenAt;

        private GapStats(String keyword) {
            this.keyword = keyword;
        }
    }
}
