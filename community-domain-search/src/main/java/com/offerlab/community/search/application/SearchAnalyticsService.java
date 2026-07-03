package com.offerlab.community.search.application;

import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.search.api.dto.SearchAnalyticsDTO;
import com.offerlab.community.search.api.dto.SearchAnalyticsItemDTO;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchAnalyticsMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchAnalyticsEventPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAnalyticsService {

    private static final String EVENT_SEARCH = "SEARCH";
    private static final String EVENT_PREP_CLICK = "PREP_CLICK";
    private static final String EVENT_COMMUNITY_RECOMMEND_CLICK = "COMMUNITY_RECOMMEND_CLICK";

    private final SearchAnalyticsMapper mapper;
    private final SnowflakeIdGenerator idGenerator;

    public void recordSearch(String keyword, String company, String position,
                             Integer postType, String sortType, int resultCount, boolean firstPage) {
        if (!firstPage) {
            return;
        }
        String cleanKeyword = clean(keyword, 100);
        String cleanCompany = clean(company, 128);
        String cleanPosition = clean(position, 128);
        if (cleanKeyword == null && cleanCompany == null && cleanPosition == null && postType == null) {
            return;
        }
        SearchAnalyticsEventPO event = baseEvent(EVENT_SEARCH);
        event.setKeyword(cleanKeyword);
        event.setCompany(cleanCompany);
        event.setPosition(cleanPosition);
        event.setPostType(postType);
        event.setSortType(clean(sortType, 16));
        event.setResultCount(Math.max(resultCount, 0));
        insertQuietly(event);
    }

    public void recordPrepClick(String keyword, String company) {
        String cleanCompany = clean(company, 128);
        if (cleanCompany == null) {
            return;
        }
        SearchAnalyticsEventPO event = baseEvent(EVENT_PREP_CLICK);
        event.setKeyword(clean(keyword, 100));
        event.setCompany(cleanCompany);
        event.setResultCount(0);
        insertQuietly(event);
    }

    public void recordCommunityRecommendClick(String keyword, String target) {
        String cleanTarget = clean(target, 128);
        if (cleanTarget == null) {
            return;
        }
        SearchAnalyticsEventPO event = baseEvent(EVENT_COMMUNITY_RECOMMEND_CLICK);
        event.setKeyword(clean(keyword, 100));
        event.setCompany(cleanTarget);
        event.setResultCount(0);
        insertQuietly(event);
    }

    public SearchAnalyticsDTO summary(int days, int limit) {
        return summary(days, limit, false);
    }

    public SearchAnalyticsDTO summary(int days, int limit, boolean includeTestData) {
        if (!tableReady()) {
            return SearchAnalyticsDTO.builder()
                    .hotKeywords(List.of())
                    .noResultKeywords(List.of())
                    .prepClicks(List.of())
                    .recommendClicks(List.of())
                    .build();
        }
        int safeDays = Math.max(1, Math.min(days, 90));
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<SearchAnalyticsItemDTO> prepClicks = mapper.topPrepClicks(safeDays, safeLimit, includeTestData)
                .stream()
                .map(this::toPrepItem)
                .toList();
        List<SearchAnalyticsItemDTO> recommendClicks = mapper.topRecommendationClicks(safeDays, safeLimit, includeTestData)
                .stream()
                .map(this::toRecommendItem)
                .toList();
        return SearchAnalyticsDTO.builder()
                .hotKeywords(mapper.topSearchKeywords(safeDays, safeLimit, includeTestData).stream().map(this::toKeywordItem).toList())
                .noResultKeywords(mapper.topNoResultKeywords(safeDays, safeLimit, includeTestData).stream().map(this::toKeywordItem).toList())
                .prepClicks(prepClicks)
                .recommendClicks(recommendClicks)
                .build();
    }

    private SearchAnalyticsEventPO baseEvent(String eventType) {
        SearchAnalyticsEventPO event = new SearchAnalyticsEventPO();
        event.setId(idGenerator.nextId());
        event.setEventType(eventType);
        event.setUid(null);
        return event;
    }

    private void insertQuietly(SearchAnalyticsEventPO event) {
        try {
            if (tableReady()) {
                mapper.insertEvent(event);
            }
        } catch (RuntimeException e) {
            log.debug("record search analytics failed: {}", e.getMessage());
        }
    }

    private boolean tableReady() {
        try {
            return mapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.debug("search analytics table check failed: {}", e.getMessage());
            return false;
        }
    }

    private SearchAnalyticsItemDTO toKeywordItem(Map<String, Object> row) {
        return SearchAnalyticsItemDTO.builder()
                .keyword(asText(row.get("keyword")))
                .count(asLong(row.get("count")))
                .noResultCount(asLong(row.get("noResultCount")))
                .lastResultCount(asLong(row.get("lastResultCount")))
                .lastSearchedAt(asTime(row.get("lastSearchedAt")))
                .build();
    }

    private SearchAnalyticsItemDTO toPrepItem(Map<String, Object> row) {
        return SearchAnalyticsItemDTO.builder()
                .company(asText(row.get("company")))
                .count(asLong(row.get("count")))
                .lastSearchedAt(asTime(row.get("lastSearchedAt")))
                .build();
    }

    private SearchAnalyticsItemDTO toRecommendItem(Map<String, Object> row) {
        String target = asText(row.get("company"));
        return SearchAnalyticsItemDTO.builder()
                .keyword(asText(row.get("keyword")))
                .company(target)
                .target(target)
                .count(asLong(row.get("count")))
                .lastSearchedAt(asTime(row.get("lastSearchedAt")))
                .build();
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isBlank()) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private static String asTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time.toString();
        }
        return value == null ? null : String.valueOf(value);
    }
}
