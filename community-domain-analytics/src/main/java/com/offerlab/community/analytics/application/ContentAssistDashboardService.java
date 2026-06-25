package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.ContentAssistDashboardDTO;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.ContentAssistDashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAssistDashboardService {

    private final ContentAssistDashboardMapper mapper;

    public ContentAssistDashboardDTO summary(int days) {
        int safeDays = Math.max(1, Math.min(days <= 0 ? 30 : days, 180));
        LocalDateTime since = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        try {
            if (mapper.tableExists() <= 0) {
                return empty(safeDays);
            }
            Map<String, Object> summary = mapper.summary(since);
            return ContentAssistDashboardDTO.builder()
                    .days(safeDays)
                    .totalRequests(asLong(summary.get("totalRequests")))
                    .aiSuccessRequests(asLong(summary.get("aiSuccessRequests")))
                    .fallbackRequests(asLong(summary.get("fallbackRequests")))
                    .ruleOnlyRequests(asLong(summary.get("ruleOnlyRequests")))
                    .uniqueUsers(asLong(summary.get("uniqueUsers")))
                    .totalPromptTokens(asLong(summary.get("totalPromptTokens")))
                    .totalCompletionTokens(asLong(summary.get("totalCompletionTokens")))
                    .estimatedCostMicros(asLong(summary.get("estimatedCostMicros")))
                    .sceneStats(toBuckets(mapper.sceneStats(since)))
                    .statusStats(toBuckets(mapper.statusStats(since)))
                    .providerStats(toBuckets(mapper.providerStats(since)))
                    .errorStats(toBuckets(mapper.errorStats(since, 10)))
                    .recentErrors(toErrors(mapper.recentErrors(since, 10)))
                    .build();
        } catch (Exception e) {
            log.warn("content assist dashboard degraded: {}", e.getMessage());
            return empty(safeDays);
        }
    }

    private ContentAssistDashboardDTO empty(int days) {
        return ContentAssistDashboardDTO.builder()
                .days(days)
                .totalRequests(0L)
                .aiSuccessRequests(0L)
                .fallbackRequests(0L)
                .ruleOnlyRequests(0L)
                .uniqueUsers(0L)
                .totalPromptTokens(0L)
                .totalCompletionTokens(0L)
                .estimatedCostMicros(0L)
                .sceneStats(List.of())
                .statusStats(List.of())
                .providerStats(List.of())
                .errorStats(List.of())
                .recentErrors(List.of())
                .build();
    }

    private List<ContentAssistDashboardDTO.BucketDTO> toBuckets(List<Map<String, Object>> rows) {
        return (rows == null ? List.<Map<String, Object>>of() : rows).stream()
                .map(row -> ContentAssistDashboardDTO.BucketDTO.builder()
                        .name(String.valueOf(row.get("name")))
                        .count(asLong(row.get("count")))
                        .estimatedCostMicros(asLong(row.get("estimatedCostMicros")))
                        .build())
                .toList();
    }

    private List<ContentAssistDashboardDTO.ErrorSampleDTO> toErrors(List<Map<String, Object>> rows) {
        return (rows == null ? List.<Map<String, Object>>of() : rows).stream()
                .map(row -> ContentAssistDashboardDTO.ErrorSampleDTO.builder()
                        .scene(stringValue(row.get("scene")))
                        .provider(stringValue(row.get("provider")))
                        .errorCode(stringValue(row.get("errorCode")))
                        .createTime((LocalDateTime) row.get("createTime"))
                        .build())
                .toList();
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
