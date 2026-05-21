package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.AnalyticsFacade;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsFacadeImpl implements AnalyticsFacade {

    private final PostMapper postMapper;
    private final TagMapper tagMapper;

    @Override
    public void track(Map<String, Object> event) {
        log.debug("track: {}", event);
    }

    @Override
    public List<Map<String, Object>> getHotPosts(int size) {
        return List.of();
    }

    @Override
    public Map<String, Object> getTrendDashboard(String range) {
        String normalizedRange = normalizeRange(range);
        int days = rangeDays(normalizedRange);
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        long total = postMapper.countPublishedSince(since);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("range", normalizedRange);
        data.put("days", days);
        data.put("totalPosts", total);
        data.put("publishTrend", fillTrend(days, postMapper.countPublishedByDate(since)));
        data.put("topCompanies", postMapper.countCompanies(since, 10));
        data.put("topTags", tagMapper.countTopTags(since, 10));
        data.put("positionDistribution", withPercentage(postMapper.countPositions(since, 10), total));
        data.put("resultDistribution", postMapper.countInterviewResults(since, 10));
        return data;
    }

    @Override
    public Map<String, Object> getPersonalDashboard(Long uid) {
        return Map.of("uid", uid, "items", List.of());
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

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }
}
