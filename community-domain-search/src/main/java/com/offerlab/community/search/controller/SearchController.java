package com.offerlab.community.search.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.search.api.SearchFacade;
import com.offerlab.community.search.api.dto.PublicSearchStatusDTO;
import com.offerlab.community.search.api.dto.SearchAnalyticsTrackCmd;
import com.offerlab.community.search.api.dto.SearchStatusDTO;
import com.offerlab.community.search.api.dto.SearchTrustFilter;
import com.offerlab.community.search.application.PostSearchIndexer;
import com.offerlab.community.search.application.SearchAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@PublicApi
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private static final String EVENT_COMMUNITY_RECOMMEND_CLICK = "COMMUNITY_RECOMMEND_CLICK";
    private static final Set<String> TRACK_EVENT_WHITELIST = Set.of(EVENT_COMMUNITY_RECOMMEND_CLICK);
    private static final int TRACK_RATE_LIMIT_PER_MINUTE = 20;
    private static final long TRACK_RATE_WINDOW_MS = 60_000L;
    private static final long TRACK_DEDUP_WINDOW_MS = 15_000L;
    private static final int TRACK_GUARD_MAX_ENTRIES = 10_000;
    private static final Cache<String, RateBucket> TRACK_RATE_BUCKETS = Caffeine.newBuilder()
            .maximumSize(TRACK_GUARD_MAX_ENTRIES)
            .expireAfterWrite(Duration.ofMillis(TRACK_RATE_WINDOW_MS))
            .build();
    private static final Cache<String, DedupReservation> TRACK_DEDUP_KEYS = Caffeine.newBuilder()
            .maximumSize(TRACK_GUARD_MAX_ENTRIES)
            .expireAfterWrite(Duration.ofMillis(TRACK_DEDUP_WINDOW_MS))
            .build();

    private final SearchFacade facade;
    private final SearchAnalyticsService searchAnalyticsService;
    private final PostFacade postFacade;
    private final PostSearchIndexer postSearchIndexer;

    @GetMapping("/posts")
    @RateLimit(key = "'public:search:posts:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<PostBriefDTO>> searchPosts(@RequestParam(name = "q", required = false) @Size(max = 100) String keyword,
                                                       @RequestParam(required = false) @Size(max = 128) String company,
                                                       @RequestParam(required = false) @Size(max = 128) String position,
                                                       @RequestParam(required = false) Integer type,
                                                       @RequestParam(required = false) @Min(1) @Max(5) Integer domain,
                                                       @RequestParam(required = false) @Size(max = 16) String sort,
                                                       @RequestParam(required = false) Boolean trustProfile,
                                                       @RequestParam(required = false) @Size(max = 40) String freshnessStatus,
                                                       @RequestParam(required = false) Boolean resolved,
                                                       @RequestParam(required = false) Boolean sourceComplete,
                                                       @RequestParam(required = false) @Size(max = 128) String cursor,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
                                                       HttpServletRequest request) {
        domain = requireOptionalDomain(domain);
        SearchTrustFilter trustFilter;
        try {
            trustFilter = SearchTrustFilter.of(trustProfile, freshnessStatus, resolved, sourceComplete);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), ex.getMessage());
        }
        return Result.ok(facade.searchPosts(keyword, company, position, type, domain, sort, cursor, size, false,
                trustFilter).publicView());
    }

    @GetMapping("/suggest")
    @RateLimit(key = "'public:search:suggest:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<List<String>> suggest(@RequestParam @Size(max = 100) String prefix,
                                        @RequestParam(defaultValue = "10") @Min(1) @Max(20) int size,
                                        HttpServletRequest request) {
        return Result.ok(facade.suggest(prefix, size));
    }

    @GetMapping("/hot")
    @RateLimit(key = "'public:search:hot:' + #request.remoteAddr", rate = 180, per = 60, failOpen = false)
    public Result<List<String>> hot(@RequestParam(defaultValue = "10") @Min(1) @Max(20) int size,
                                    HttpServletRequest request) {
        return Result.ok(facade.getHotKeywords(size));
    }

    @GetMapping("/status")
    @RateLimit(key = "'public:search:status:' + #request.remoteAddr", rate = 300, per = 60, failOpen = false)
    public Result<PublicSearchStatusDTO> status(HttpServletRequest request) {
        return Result.ok(toPublicStatus(postSearchIndexer.publicStatus()));
    }

    @PostMapping("/analytics/track")
    @RateLimit(key = "'search:analytics:track:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<Map<String, Object>> track(@Valid @RequestBody(required = false) SearchAnalyticsTrackCmd cmd,
                                             HttpServletRequest request) {
        String eventType = normalizeEventType(cmd == null ? null : cmd.getEventType());
        if (eventType == null || !TRACK_EVENT_WHITELIST.contains(eventType)) {
            return trackResult(false, null);
        }

        String keyword = cleanTrackText(cmd.getKeyword(), 100, false);
        String target = cleanTrackText(firstText(cmd.getTarget(), cmd.getCompany()), 128, true);
        if (target == null || (cmd.getKeyword() != null && keyword == null)) {
            return trackResult(false, null);
        }

        String fingerprint = sha256Hex(clientFingerprint(request));
        if (!allowTrackRate(fingerprint)) {
            return trackResult(false, null);
        }

        String dedupKey = sha256Hex(fingerprint + "|" + eventType + "|" + nullToEmpty(keyword) + "|" + target);
        DedupReservation reservation = reserveDedupKey(dedupKey);
        if (reservation == null) {
            return trackResult(false, null);
        }

        boolean tracked = searchAnalyticsService.recordCommunityRecommendClick(keyword, target);
        if (!tracked) {
            TRACK_DEDUP_KEYS.asMap().remove(dedupKey, reservation);
        }
        return trackResult(tracked, null);
    }

    private static String firstText(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private static Result<Map<String, Object>> trackResult(boolean tracked, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tracked", tracked);
        if (reason != null) {
            data.put("reason", reason);
        }
        return Result.ok(data);
    }

    private static String normalizeEventType(String eventType) {
        if (eventType == null) {
            return null;
        }
        String normalized = eventType.trim().toUpperCase();
        return normalized.isBlank() || normalized.length() > 32 ? null : normalized;
    }

    private static String cleanTrackText(String value, int maxLength, boolean required) {
        if (value == null) {
            return required ? null : null;
        }
        String text = value.trim();
        if (text.isBlank()) {
            return required ? null : null;
        }
        if (text.length() > maxLength || looksLikeStructuredOrSecret(text)) {
            return null;
        }
        return text;
    }

    private static boolean looksLikeStructuredOrSecret(String text) {
        String compact = text.replace(" ", "");
        return text.contains("{")
                || text.contains("}")
                || text.contains("[")
                || text.contains("]")
                || text.contains("\n")
                || text.contains("\r")
                || compact.matches("(?i).*\\b(token|secret|password|authorization|api[_-]?key)\\b.*")
                || compact.matches("[A-Za-z0-9_-]{24,}")
                || text.matches("(?i)^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")
                || text.matches("(?i)^(?:https?://|www\\.)\\S+$");
    }

    private static String clientFingerprint(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        return nullToEmpty(ip) + "|" + truncate(nullToEmpty(userAgent), 120);
    }

    private static boolean allowTrackRate(String fingerprint) {
        RateBucket bucket = TRACK_RATE_BUCKETS.get(fingerprint, key -> new RateBucket());
        return bucket.count.incrementAndGet() <= TRACK_RATE_LIMIT_PER_MINUTE;
    }

    private static DedupReservation reserveDedupKey(String dedupKey) {
        DedupReservation reservation = new DedupReservation();
        return TRACK_DEDUP_KEYS.asMap().putIfAbsent(dedupKey, reservation) == null ? reservation : null;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", bytes[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(nullToEmpty(value).hashCode());
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static final class RateBucket {
        private final AtomicInteger count = new AtomicInteger();
    }

    private static final class DedupReservation {
    }

    @GetMapping("/posts/{postId}/publish-status")
    @RateLimit(key = "'public:search:publish-status:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<Map<String, Object>> publishStatus(@PathVariable @Positive Long postId,
                                                     HttpServletRequest request) {
        PostBriefDTO publicBrief = postFacade.batchGetPosts(List.of(postId)).get(postId);
        boolean dbVisible = publicBrief != null;

        PageResult<PostBriefDTO> recall = facade.searchPosts(String.valueOf(postId), null, null, null,
                "relevance", null, 5, false);
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("publiclyVisible", dbVisible);

        Map<String, Object> search = new LinkedHashMap<>();
        search.put("visible", containsPost(recall, postId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("postId", postId);
        data.put("database", database);
        data.put("search", search);
        data.put("ready", dbVisible && Boolean.TRUE.equals(search.get("visible")));
        return Result.ok(data);
    }

    private static boolean containsPost(PageResult<PostBriefDTO> page, Long postId) {
        return page != null && page.getItems() != null && page.getItems().stream()
                .anyMatch(post -> java.util.Objects.equals(post.getId(), postId));
    }

    private static Integer requireOptionalDomain(Integer domain) {
        if (domain == null) {
            return null;
        }
        if (domain < 1 || domain > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "频道不存在或已下线");
        }
        return domain;
    }

    private static PublicSearchStatusDTO toPublicStatus(SearchStatusDTO status) {
        boolean available = status != null && Boolean.TRUE.equals(status.getPublicSearchAvailable());
        boolean degraded = available && Boolean.TRUE.equals(status.getPublicSearchDegraded());
        if (!available) {
            return PublicSearchStatusDTO.builder()
                    .available(false)
                    .degraded(false)
                    .message("公开搜索暂不可用，请稍后重试或使用发现页。")
                    .action("查看发现页")
                    .build();
        }
        if (degraded) {
            return PublicSearchStatusDTO.builder()
                    .available(true)
                    .degraded(true)
                    .message("搜索仍可使用，但结果完整度和排序能力可能暂时受限。")
                    .action("继续搜索")
                    .build();
        }
        return PublicSearchStatusDTO.builder()
                .available(true)
                .degraded(false)
                .message("搜索状态正常，只返回公开且符合当前条件的内容。")
                .build();
    }

}
