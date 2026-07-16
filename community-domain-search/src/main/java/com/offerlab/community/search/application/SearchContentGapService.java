package com.offerlab.community.search.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.CollaborationModels.NeedDTO;
import com.offerlab.community.post.collaboration.api.CollaborationModels.SearchGapNeedCreateCmd;
import com.offerlab.community.post.collaboration.application.CollaborationService;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.search.api.dto.SearchContentGapConvertCmd;
import com.offerlab.community.search.api.dto.SearchContentGapDTO;
import com.offerlab.community.search.api.dto.SearchContentGapResolveCmd;
import com.offerlab.community.search.api.dto.SearchContentGapReviewCmd;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchAnalyticsMapper;
import com.offerlab.community.search.infrastructure.persistence.mapper.SearchContentGapMapper;
import com.offerlab.community.search.infrastructure.persistence.po.SearchContentGapPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SearchContentGapService {

    static final int MIN_SAMPLE_COUNT = 5;
    private static final int WEAK_RESULT_THRESHOLD = 2;
    private static final int MAX_LIMIT = 100;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(?:\\+?\\d[\\d\\s().-]{7,}\\d)$");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)^(?:https?://|www\\.)\\S+$");
    private static final Pattern JWT_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{8,}$");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{24,}$");

    private final SearchAnalyticsMapper analyticsMapper;
    private final SearchContentGapMapper gapMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final CollaborationService collaborationService;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final AdminAuditService adminAuditService;

    /**
     * Retains the package-level analytics projection constructor used by the existing focused test.
     * Spring always uses the full injected constructor above; production callers never receive an in-memory fallback.
     */
    @Deprecated
    SearchContentGapService(SearchAnalyticsMapper analyticsMapper) {
        this.analyticsMapper = analyticsMapper;
        this.gapMapper = null;
        this.idGenerator = null;
        this.collaborationService = null;
        this.adminPermissionService = null;
        this.domainModeratorService = null;
        this.adminAuditService = null;
    }

    /**
     * Refreshes the persisted search-gap projection from aggregate analytics.
     * This endpoint intentionally returns no in-memory fallback: governance can only act on durable gaps.
     */
    public List<SearchContentGapDTO> candidates(int days, int limit) {
        return candidates(days, limit, false);
    }

    public List<SearchContentGapDTO> candidates(int days, int limit, boolean includeTestData) {
        if (!analyticsTableReady()) {
            return List.of();
        }
        int safeDays = Math.max(1, Math.min(days, 90));
        int safeLimit = safeLimit(limit);
        Map<String, GapStats> statsByKeyword = new LinkedHashMap<>();
        analyticsMapper.topSearchKeywords(safeDays, safeLimit, includeTestData)
                .forEach(row -> merge(statsByKeyword, row, SearchContentGapDTO.CreatedFrom.hot_keyword));
        analyticsMapper.topNoResultKeywords(safeDays, safeLimit, includeTestData)
                .forEach(row -> merge(statsByKeyword, row, SearchContentGapDTO.CreatedFrom.no_result));

        if (gapMapper == null) {
            return statsByKeyword.values().stream()
                    .map(stats -> toCandidate(stats, safeDays))
                    .filter(Objects::nonNull)
                    .toList();
        }
        if (!gapTableReady()) {
            return List.of();
        }

        List<SearchContentGapDTO> persisted = new ArrayList<>();
        for (GapStats stats : statsByKeyword.values()) {
            SearchContentGapDTO candidate = toCandidate(stats, safeDays);
            if (candidate == null) {
                continue;
            }
            gapMapper.upsertCandidate(toPersistence(candidate));
            SearchContentGapPO saved = gapMapper.findByGapKey(candidate.getGapId());
            if (saved != null) {
                persisted.add(toDto(saved));
            }
        }
        return persisted;
    }

    @Scheduled(fixedDelayString = "${offerlab.search.content-gap.refresh-ms:900000}")
    public void refreshCandidates() {
        try {
            candidates(30, 50, false);
        } catch (RuntimeException e) {
            log.warn("search content gap projection refresh failed", e);
        }
    }

    /**
     * Kept for downstream callers. Only a governed, persisted approval can enter this list.
     */
    public List<SearchContentGapDTO> downstreamApprovedGaps(int days, int limit) {
        if (!gapTableReady()) {
            return List.of();
        }
        return gapMapper.listApprovedForDownstream(safeLimit(limit)).stream()
                .map(this::toDto)
                .toList();
    }

    public List<SearchContentGapDTO> list(String status, String riskLevel, Integer domain, int limit, Long operatorUid) {
        requireListGovernance(operatorUid, domain);
        requireGapTable();
        return gapMapper.list(normalizeStatus(status), normalizeRiskLevel(riskLevel),
                        optionalDomain(domain), safeLimit(limit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SearchContentGapDTO approve(String gapKey, SearchContentGapReviewCmd cmd, Long operatorUid) {
        requireGapTable();
        SearchContentGapPO before = requireGap(gapKey);
        Integer domain = requireActionDomain(before, cmd == null ? null : cmd.getDomain());
        requireGovernance(operatorUid, domain);
        String note = RiskConfirmation.requireHigh(cmd == null ? null : cmd.getNote());
        requireHighRiskAcknowledgement(before, cmd == null ? null : cmd.getRiskAcknowledged(), operatorUid);
        if ("APPROVED".equals(before.getGapStatus())) {
            return toDto(before);
        }
        if (!isActionable(before, "CANDIDATE", "REVIEW_REQUIRED")) {
            throw invalidState();
        }
        if (gapMapper.approve(before.getId(), domain, operatorUid, note) != 1) {
            throw invalidState();
        }
        SearchContentGapPO after = requireGap(gapKey);
        adminAuditService.recordRequired(operatorUid, "SEARCH_CONTENT_GAP_APPROVE", "SEARCH_CONTENT_GAP",
                before.getId(), before, after, note);
        return toDto(after);
    }

    @Transactional
    public SearchContentGapDTO ignore(String gapKey, SearchContentGapReviewCmd cmd, Long operatorUid) {
        requireGapTable();
        SearchContentGapPO before = requireGap(gapKey);
        Integer domain = requireActionDomain(before, cmd == null ? null : cmd.getDomain());
        requireGovernance(operatorUid, domain);
        String note = RiskConfirmation.requireHigh(cmd == null ? null : cmd.getNote());
        if ("IGNORED".equals(before.getGapStatus())) {
            return toDto(before);
        }
        if (!isActionable(before, "CANDIDATE", "REVIEW_REQUIRED", "APPROVED")) {
            throw invalidState();
        }
        if (gapMapper.ignore(before.getId(), domain, operatorUid, note) != 1) {
            throw invalidState();
        }
        SearchContentGapPO after = requireGap(gapKey);
        adminAuditService.recordRequired(operatorUid, "SEARCH_CONTENT_GAP_IGNORE", "SEARCH_CONTENT_GAP",
                before.getId(), before, after, note);
        return toDto(after);
    }

    @Transactional
    public SearchContentGapDTO convert(String gapKey, SearchContentGapConvertCmd cmd, Long operatorUid) {
        requireGapTable();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        SearchContentGapPO before = requireGap(gapKey);
        Integer domain = requiredDomain(cmd.getDomain());
        requireGovernance(operatorUid, domain);
        String note = RiskConfirmation.requireCritical(cmd.getNote(), cmd.getConfirmationPhrase());
        requireHighRiskAcknowledgement(before, cmd.getRiskAcknowledged(), operatorUid);
        if ("CONVERTED".equals(before.getGapStatus()) || "FULFILLED".equals(before.getGapStatus())) {
            return toDto(before);
        }
        if (!"APPROVED".equals(before.getGapStatus())) {
            throw invalidState();
        }
        if (!Objects.equals(before.getMinSampleMet(), 1)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "search gaps require the minimum aggregate sample before conversion");
        }
        if (before.getDomain() != null && !Objects.equals(before.getDomain(), domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "gap domain cannot change during conversion");
        }

        NeedDTO need = collaborationService.createOrGetSearchGapNeed(before.getId(),
                toSearchGapNeedCmd(before, cmd, domain), operatorUid);
        if (gapMapper.convert(before.getId(), domain, need.getId(), operatorUid, note) != 1) {
            throw invalidState();
        }
        SearchContentGapPO after = requireGap(gapKey);
        adminAuditService.recordRequired(operatorUid, "SEARCH_CONTENT_GAP_CONVERT", "SEARCH_CONTENT_GAP",
                before.getId(), before, after, note);
        return toDto(after);
    }

    /**
     * Closes an approved gap when governed review confirms that an existing result already satisfies it.
     */
    @Transactional
    public SearchContentGapDTO resolve(String gapKey, SearchContentGapResolveCmd cmd, Long operatorUid) {
        requireGapTable();
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        SearchContentGapPO before = requireGap(gapKey);
        Integer domain = requiredDomain(before.getDomain());
        requireGovernance(operatorUid, domain);
        String note = RiskConfirmation.requireCritical(cmd.getNote(), cmd.getConfirmationPhrase());
        requireHighRiskAcknowledgement(before, cmd.getRiskAcknowledged(), operatorUid);
        if ("FULFILLED".equals(before.getGapStatus())) {
            return toDto(before);
        }
        if (!"APPROVED".equals(before.getGapStatus())) {
            throw invalidState();
        }
        Resolution resolution = normalizeResolution(cmd);
        if (gapMapper.resolve(before.getId(), resolution.type(), resolution.id(), resolution.postId(),
                operatorUid, note) != 1) {
            throw invalidState();
        }
        SearchContentGapPO after = requireGap(gapKey);
        adminAuditService.recordRequired(operatorUid, "SEARCH_CONTENT_GAP_RESOLVE", "SEARCH_CONTENT_GAP",
                before.getId(), before, after, note);
        return toDto(after);
    }

    /**
     * Invoked from the existing collaboration contribution event after the source transaction commits.
     * The conditional UPDATE makes replayed Spring/outbox events a no-op after the first successful callback.
     */
    @Transactional
    public void fulfillFromContentNeed(Long needId, String resolutionType, Long resolutionId, Long resolutionPostId) {
        if (needId == null || needId <= 0 || !gapTableReady()) {
            return;
        }
        try {
            gapMapper.fulfillFromNeed(needId, normalizeResolutionType(resolutionType),
                    positiveOrNull(resolutionId), positiveOrNull(resolutionPostId));
        } catch (RuntimeException e) {
            log.warn("search content gap fulfillment callback failed: needId={}", needId, e);
            throw e;
        }
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

    private SearchContentGapDTO toCandidate(GapStats stats, int days) {
        boolean minSampleMet = stats.searchCount >= MIN_SAMPLE_COUNT;
        SearchContentGapDTO.RiskLevel riskLevel = riskLevel(stats.keyword, minSampleMet);
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
                .status(status(minSampleMet, riskLevel))
                .source("search")
                .sourceRefs(List.of("analytics:search:" + stableHash(stats.keyword)))
                .createdFrom(createdFrom)
                .lastSeenAt(stats.lastSeenAt)
                .build();
    }

    private SearchContentGapPO toPersistence(SearchContentGapDTO dto) {
        SearchContentGapPO po = new SearchContentGapPO();
        po.setId(idGenerator.nextId());
        po.setGapKey(dto.getGapId());
        po.setKeyword(dto.getKeyword());
        po.setClusterId(dto.getClusterId());
        po.setReasonText(dto.getReasonText());
        po.setWindowDays(dto.getWindowDays());
        po.setSearchCount(dto.getSearchCount());
        po.setNoResultCount(dto.getNoResultCount());
        po.setWeakResultCount(dto.getWeakResultCount());
        po.setMinSampleMet(Boolean.TRUE.equals(dto.getMinSampleMet()) ? 1 : 0);
        po.setRiskLevel(dto.getRiskLevel().name());
        po.setTargetStage(dto.getTargetStage().name());
        po.setGapStatus(dto.getStatus().name());
        po.setSource(dto.getSource());
        po.setSourceRefsJson(toSourceRefsJson(dto.getSourceRefs()));
        po.setCreatedFrom(dto.getCreatedFrom().name());
        po.setLastSeenAt(parseTime(dto.getLastSeenAt()));
        return po;
    }

    private SearchContentGapDTO toDto(SearchContentGapPO po) {
        return SearchContentGapDTO.builder()
                .id(po.getId())
                .gapId(po.getGapKey())
                .keyword(po.getKeyword())
                .clusterId(po.getClusterId())
                .reasonText(po.getReasonText())
                .windowDays(po.getWindowDays())
                .searchCount(nonNegative(po.getSearchCount()))
                .noResultCount(nonNegative(po.getNoResultCount()))
                .weakResultCount(nonNegative(po.getWeakResultCount()))
                .minSampleMet(Objects.equals(po.getMinSampleMet(), 1))
                .riskLevel(enumValue(SearchContentGapDTO.RiskLevel.class, po.getRiskLevel(),
                        SearchContentGapDTO.RiskLevel.MEDIUM))
                .targetStage(enumValue(SearchContentGapDTO.TargetStage.class, po.getTargetStage(),
                        SearchContentGapDTO.TargetStage.knowledge))
                .status(enumValue(SearchContentGapDTO.GapStatus.class, po.getGapStatus(),
                        SearchContentGapDTO.GapStatus.REVIEW_REQUIRED))
                .source(po.getSource())
                .sourceRefs(fromSourceRefsJson(po.getSourceRefsJson()))
                .createdFrom(enumValue(SearchContentGapDTO.CreatedFrom.class, po.getCreatedFrom(),
                        SearchContentGapDTO.CreatedFrom.manual_review))
                .lastSeenAt(po.getLastSeenAt() == null ? null : po.getLastSeenAt().toString())
                .domain(po.getDomain())
                .reviewedBy(po.getReviewedBy())
                .reviewNote(po.getReviewNote())
                .reviewedAt(po.getReviewedAt())
                .convertedNeedId(po.getConvertedNeedId())
                .resolutionType(po.getResolutionType())
                .resolutionId(po.getResolutionId())
                .resolutionPostId(po.getResolutionPostId())
                .fulfilledAt(po.getFulfilledAt())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private SearchGapNeedCreateCmd toSearchGapNeedCmd(SearchContentGapPO gap,
                                                       SearchContentGapConvertCmd cmd,
                                                       Integer domain) {
        SearchGapNeedCreateCmd need = new SearchGapNeedCreateCmd();
        need.setDomain(domain);
        need.setContentFormat(clean(cmd.getContentFormat(), 32, "GUIDE"));
        need.setTitle(clean(cmd.getTitle(), 120, "Community demand: " + gap.getKeyword()));
        need.setDescription(clean(cmd.getDescription(), 2000,
                "This content need was created from aggregated anonymous search demand for: " + gap.getKeyword()));
        need.setAcceptanceCriteria(clean(cmd.getAcceptanceCriteria(), 1000,
                "Provide a reusable, bounded answer with context, method, outcome, and limitations."));
        need.setRiskAcknowledged(cmd.getRiskAcknowledged());
        return need;
    }

    private SearchContentGapPO requireGap(String gapKey) {
        String key = requiredGapKey(gapKey);
        SearchContentGapPO gap = gapMapper.lockByGapKey(key);
        if (gap == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return gap;
    }

    private void requireListGovernance(Long uid, Integer domain) {
        if (isGlobalGovernance(uid)) {
            return;
        }
        if (domain != null && domainModeratorService.canModerateDomain(uid, requiredDomain(domain))) {
            return;
        }
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    private void requireGovernance(Long uid, Integer domain) {
        if (isGlobalGovernance(uid)
                || (domain != null && domainModeratorService.canModerateDomain(uid, domain))) {
            return;
        }
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    private void requireHighRiskAcknowledgement(SearchContentGapPO gap, Boolean acknowledged, Long uid) {
        if (!"HIGH".equals(gap.getRiskLevel())) {
            return;
        }
        adminPermissionService.requireScope(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR);
        if (!Boolean.TRUE.equals(acknowledged)) {
            throw new BizException(ErrorCode.INVALID_REQUEST.getCode(),
                    "high-risk search gaps require explicit governance acknowledgement");
        }
    }

    private boolean isGlobalGovernance(Long uid) {
        return uid != null && (adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode());
    }

    private Integer requireActionDomain(SearchContentGapPO gap, Integer requestedDomain) {
        Integer gapDomain = gap.getDomain();
        if (requestedDomain != null && gapDomain != null && !Objects.equals(requestedDomain, gapDomain)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "gap domain cannot be reassigned");
        }
        return requiredDomain(requestedDomain == null ? gapDomain : requestedDomain);
    }

    private void requireGapTable() {
        if (!gapTableReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Search content gap migration is required: db/migration/20260715_trusted_distribution_revisit.sql");
        }
    }

    private boolean gapTableReady() {
        try {
            return gapMapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.debug("search content gap table check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean analyticsTableReady() {
        try {
            return analyticsMapper.tableExists() > 0;
        } catch (RuntimeException e) {
            log.debug("search analytics table check failed: {}", e.getMessage());
            return false;
        }
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
        if (lower.contains("fallback") || lower.contains("demo") || looksSensitive(text)) {
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
                || lower.contains("\u4e2a\u4eba\u7b80\u5386")
                || lower.contains("\u6211\u7684\u7b80\u5386")
                || lower.contains("\u6211\u7684\u9762\u8bd5")
                || lower.contains("\u79c1\u4eba");
    }

    private static SearchContentGapDTO.RiskLevel riskLevel(String keyword, boolean minSampleMet) {
        if (looksPrivateTraining(keyword)) {
            return SearchContentGapDTO.RiskLevel.HIGH;
        }
        return minSampleMet ? SearchContentGapDTO.RiskLevel.LOW : SearchContentGapDTO.RiskLevel.MEDIUM;
    }

    private static SearchContentGapDTO.GapStatus status(boolean minSampleMet, SearchContentGapDTO.RiskLevel riskLevel) {
        if (SearchContentGapDTO.RiskLevel.HIGH.equals(riskLevel) || !minSampleMet) {
            return SearchContentGapDTO.GapStatus.REVIEW_REQUIRED;
        }
        return SearchContentGapDTO.GapStatus.CANDIDATE;
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

    private static String toSourceRefsJson(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return "[]";
        }
        return refs.stream()
                .filter(StringUtils::hasText)
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]")
                .orElse("[]");
    }

    private static List<String> fromSourceRefsJson(String json) {
        if (!StringUtils.hasText(json) || "[]".equals(json.trim())) {
            return List.of();
        }
        String value = json.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isBlank()) {
            return List.of();
        }
        List<String> refs = new ArrayList<>();
        for (String part : value.split("\",\"")) {
            String ref = part.replaceAll("^\"|\"$", "")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            if (StringUtils.hasText(ref)) {
                refs.add(ref);
            }
        }
        return List.copyOf(refs);
    }

    private static LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Resolution normalizeResolution(SearchContentGapResolveCmd cmd) {
        String type = normalizeResolutionType(cmd.getResolutionType());
        Long id = cmd.getResolutionId() == null ? cmd.getResolutionPostId() : cmd.getResolutionId();
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long postId = positiveOrNull(cmd.getResolutionPostId());
        if (postId != null && ("POST".equals(type) || "QUESTION".equals(type)) && !Objects.equals(id, postId)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "resolutionId and resolutionPostId must identify the same post");
        }
        if ("SERIES".equals(type) && postId != null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "resolutionPostId is only compatible with POST or QUESTION");
        }
        return new Resolution(type, id, postId);
    }

    private static String normalizeResolutionType(String value) {
        String type = clean(value, 24, null);
        if (type == null) {
            return "POST";
        }
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "POST", "QUESTION", "SERIES" -> type.toUpperCase(Locale.ROOT);
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        };
    }

    private static String normalizeStatus(String value) {
        String status = clean(value, 24, null);
        if (status == null) {
            return null;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "CANDIDATE", "REVIEW_REQUIRED", "APPROVED", "IGNORED", "CONVERTED", "FULFILLED"
                    -> status.toUpperCase(Locale.ROOT);
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        };
    }

    private static String normalizeRiskLevel(String value) {
        String riskLevel = clean(value, 12, null);
        if (riskLevel == null) {
            return null;
        }
        return switch (riskLevel.toUpperCase(Locale.ROOT)) {
            case "LOW", "MEDIUM", "HIGH" -> riskLevel.toUpperCase(Locale.ROOT);
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        };
    }

    private static Integer requiredDomain(Integer domain) {
        if (!PostDomain.isValid(domain)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return domain;
    }

    private static Integer optionalDomain(Integer domain) {
        return domain == null ? null : requiredDomain(domain);
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 30 : limit, MAX_LIMIT));
    }

    private static String requiredGapKey(String value) {
        String key = clean(value, 80, null);
        if (key == null || !key.matches("search-gap-[a-f0-9]{8,64}")) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return key;
    }

    private static boolean isActionable(SearchContentGapPO gap, String... statuses) {
        for (String status : statuses) {
            if (status.equals(gap.getGapStatus())) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String value, int max, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    private static Long positiveOrNull(Long value) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static Long nonNegative(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
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

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            try {
                return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private static BizException invalidState() {
        return new BizException(ErrorCode.INVALID_STATUS);
    }

    private record Resolution(String type, Long id, Long postId) {
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
