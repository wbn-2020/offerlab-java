package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.GrowthEventSummaryDTO;
import com.offerlab.community.analytics.api.dto.GrowthFunnelDTO;
import com.offerlab.community.analytics.api.dto.GrowthEventTrackCmd;
import com.offerlab.community.analytics.infrastructure.persistence.mapper.GrowthEventMapper;
import com.offerlab.community.analytics.infrastructure.persistence.po.GrowthEventPO;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.post.domain.model.PostDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthEventService {

    public static final String PUBLIC_POST_VIEW = "PUBLIC_POST_VIEW";
    public static final String AUTH_REDIRECT_CLICK = "AUTH_REDIRECT_CLICK";
    public static final String POST_LIKE = "POST_LIKE";
    public static final String POST_FAVORITE = "POST_FAVORITE";
    public static final String POST_COMMENT = "POST_COMMENT";
    public static final String FIRST_POST_PUBLISHED = "FIRST_POST_PUBLISHED";
    public static final String USER_REGISTER = "USER_REGISTER";
    public static final String CROSS_DOMAIN_CONSUME = "CROSS_DOMAIN_CONSUME";
    public static final String OPERATION_CURATION_SELECTED = "OPERATION_CURATION_SELECTED";

    private static final Set<String> ALLOWED_EVENTS = Set.of(
            PUBLIC_POST_VIEW, AUTH_REDIRECT_CLICK, POST_LIKE, POST_FAVORITE,
            POST_COMMENT, FIRST_POST_PUBLISHED, USER_REGISTER, CROSS_DOMAIN_CONSUME,
            OPERATION_CURATION_SELECTED
    );
    private static final Set<String> CLIENT_TRACKABLE_EVENTS = Set.of(
            AUTH_REDIRECT_CLICK
    );
    private static final Set<String> ANONYMOUS_ALLOWED_CLIENT_EVENTS = Set.of(
            AUTH_REDIRECT_CLICK
    );

    private final GrowthEventMapper mapper;
    private final SnowflakeIdGenerator idGenerator;

    public boolean track(GrowthEventTrackCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String eventType = normalizeEventType(cmd.getEventType());
        if (!CLIENT_TRACKABLE_EVENTS.contains(eventType)) {
            return false;
        }
        Long uid = UserContext.get();
        if (uid == null && !ANONYMOUS_ALLOWED_CLIENT_EVENTS.contains(eventType)) {
            return false;
        }
        GrowthEventPO event = newEvent(
                eventType,
                uid,
                cmd.getDomain(),
                cmd.getContentId(),
                cmd.getTargetType(),
                cmd.getTargetValue(),
                cmd.getSourcePage());
        event.setExtJson(clean(cmd.getExtJson(), 2000));
        insertQuietly(event);
        return true;
    }

    public void recordTrustedEvent(String eventType, Long uid, Integer domain, Long contentId,
                                   String targetType, String targetValue, String sourcePage) {
        insertQuietly(newEvent(eventType, uid, domain, contentId, targetType, targetValue, sourcePage));
    }

    public GrowthEventSummaryDTO summary(int days, Integer domain) {
        if (!tableReady()) {
            return GrowthEventSummaryDTO.builder()
                    .days(normalizeDays(days))
                    .activeDomain(domain)
                    .total(0L)
                    .eventDistribution(List.of())
                    .domainDistribution(List.of())
                    .dailyTrend(List.of())
                    .build();
        }
        int safeDays = normalizeDays(days);
        Integer activeDomain = normalizeDomain(domain);
        LocalDateTime since = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        long total = mapper.countTotal(since, activeDomain);
        return GrowthEventSummaryDTO.builder()
                .days(safeDays)
                .activeDomain(activeDomain)
                .total(total)
                .eventDistribution(mapper.countByEventType(since, activeDomain))
                .domainDistribution(labelDomains(mapper.countByDomain(since)))
                .dailyTrend(fillTrend(safeDays, mapper.countByDate(since, activeDomain)))
                .build();
    }

    public GrowthFunnelDTO funnel(int days, Integer domain) {
        int safeDays = normalizeDays(days);
        Integer activeDomain = normalizeDomain(domain);
        if (!tableReady()) {
            return GrowthFunnelDTO.builder()
                    .days(safeDays)
                    .activeDomain(activeDomain)
                    .visitCount(0L)
                    .registerCount(0L)
                    .interactionCount(0L)
                    .firstPublishCount(0L)
                    .revisitCount(0L)
                    .build();
        }
        LocalDateTime since = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();
        Map<String, Long> counts = toCountMap(mapper.countByEventType(since, activeDomain));
        return GrowthFunnelDTO.builder()
                .days(safeDays)
                .activeDomain(activeDomain)
                .visitCount(count(counts, PUBLIC_POST_VIEW))
                .registerCount(count(counts, USER_REGISTER))
                .interactionCount(count(counts, POST_LIKE) + count(counts, POST_FAVORITE) + count(counts, POST_COMMENT))
                .firstPublishCount(count(counts, FIRST_POST_PUBLISHED))
                .revisitCount(count(counts, CROSS_DOMAIN_CONSUME))
                .build();
    }

    private void insertQuietly(GrowthEventPO event) {
        try {
            if (tableReady()) {
                mapper.insertEvent(event);
            }
        } catch (RuntimeException e) {
            log.debug("record growth event failed: {}", e.getMessage());
        }
    }

    private GrowthEventPO newEvent(String eventType, Long uid, Integer domain, Long contentId,
                                   String targetType, String targetValue, String sourcePage) {
        GrowthEventPO event = new GrowthEventPO();
        event.setId(idGenerator.nextId());
        event.setEventType(normalizeEventType(eventType));
        event.setUid(uid);
        event.setDomain(normalizeDomain(domain));
        event.setContentId(contentId != null && contentId > 0 ? contentId : null);
        event.setTargetType(clean(targetType, 32));
        event.setTargetValue(clean(targetValue, 128));
        event.setSourcePage(clean(sourcePage, 128));
        return event;
    }

    private boolean tableReady() {
        try {
            return mapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.debug("growth event table check failed: {}", e.getMessage());
            return false;
        }
    }

    private String normalizeEventType(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_EVENTS.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "eventType is invalid");
        }
        return normalized;
    }

    private Integer normalizeDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private int normalizeDays(int days) {
        return Math.max(1, Math.min(days <= 0 ? 30 : days, 90));
    }

    private String clean(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private List<Map<String, Object>> fillTrend(int days, List<Map<String, Object>> rows) {
        Map<String, Long> counts = rows == null ? Map.of() : rows.stream()
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

    private List<Map<String, Object>> labelDomains(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(row -> {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    Integer code = asInteger(row.get("name"));
                    copy.put("name", code == null || code == 0 ? "ALL" : PostDomain.fromCode(code).getDisplayName());
                    copy.put("domain", code);
                    return copy;
                })
                .toList();
    }

    private Map<String, Long> toCountMap(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("name")),
                        row -> asLong(row.get("count")),
                        Long::sum,
                        LinkedHashMap::new
                ));
    }

    private long count(Map<String, Long> counts, String eventType) {
        if (counts == null || eventType == null) {
            return 0L;
        }
        return counts.getOrDefault(eventType, 0L);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
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
