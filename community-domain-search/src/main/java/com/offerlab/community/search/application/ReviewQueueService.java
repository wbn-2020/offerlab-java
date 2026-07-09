package com.offerlab.community.search.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueuePublisher;
import com.offerlab.community.infra.review.ReviewQueueSourceActionHandler;
import com.offerlab.community.infra.review.ReviewQueueSourceDomainResolver;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.search.api.dto.ReviewQueueCreateCmd;
import com.offerlab.community.search.infrastructure.persistence.mapper.ReviewQueueMapper;
import com.offerlab.community.search.infrastructure.persistence.po.ReviewQueueItemPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class ReviewQueueService implements ReviewQueuePublisher {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int ACCESSIBLE_SCAN_BATCH_SIZE = 100;
    private static final int MAX_ACCESSIBLE_SCAN_ROWS = 5000;
    private static final String SOURCE_CONTACT_REQUEST_REPORT = "CONTACT_REQUEST_REPORT";
    private static final List<String> OPEN_STATUSES = List.of(ReviewQueueMapper.STATUS_PENDING, ReviewQueueMapper.STATUS_CLAIMED);

    private final ReviewQueueMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final AdminAuditService auditService;
    private final MigrationCheckService migrationCheckService;
    private final List<ReviewQueueSourceActionHandler> sourceActionHandlers;
    private final List<ReviewQueueSourceDomainResolver> sourceDomainResolvers;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;

    public List<ReviewQueueItemPO> list(String status, String sourceType, String riskLevel, int limit) {
        return list(status, sourceType, riskLevel, limit, null);
    }

    public List<ReviewQueueItemPO> list(String status, String sourceType, String riskLevel, int limit, Long operatorUid) {
        if (!queueReady()) {
            return List.of();
        }
        if (operatorUid == null) {
            return mapper.list(normalizeStatus(status), normalizeSourceType(sourceType), normalizeRiskLevel(riskLevel), clamp(limit));
        }
        requireQueueAccess(operatorUid);
        int requestedLimit = clamp(limit);
        if (hasGlobalReviewScope(operatorUid)) {
            return mapper.list(normalizeStatus(status), normalizeSourceType(sourceType),
                    normalizeRiskLevel(riskLevel), requestedLimit);
        }
        return listAccessibleItems(operatorUid, normalizeStatus(status), normalizeSourceType(sourceType),
                normalizeRiskLevel(riskLevel), requestedLimit).items();
    }

    public Map<String, Object> status() {
        return status(null);
    }

    public Map<String, Object> status(Long operatorUid) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String status : List.of("pending", "claimed", "approved", "rejected", "closed")) {
            byStatus.put(status, 0L);
        }
        if (!queueReady()) {
            return Map.of("available", false, "status", "DOWN", "byStatus", byStatus);
        }
        if (operatorUid != null) {
            requireQueueAccess(operatorUid);
        }
        if (operatorUid != null && !hasGlobalReviewScope(operatorUid)) {
            AccessibleScanResult scan = listAccessibleItems(operatorUid, null, null, null, MAX_ACCESSIBLE_SCAN_ROWS);
            for (ReviewQueueItemPO item : scan.items()) {
                byStatus.computeIfPresent(item.getQueueStatus(), (key, value) -> value + 1);
            }
            return Map.of("available", true, "status", "UP", "byStatus", byStatus,
                    "scannedRows", scan.scannedRows(), "scanLimited", scan.scanLimited());
        }
        for (Map<String, Object> row : mapper.countByStatus()) {
            byStatus.put(String.valueOf(row.get("status")), asLong(row.get("count")));
        }
        return Map.of("available", true, "status", "UP", "byStatus", byStatus);
    }

    public ReviewQueueItemPO findSourceItem(String sourceType, Long sourceId) {
        if (!queueReady() || sourceId == null || sourceId <= 0) {
            return null;
        }
        return mapper.findBySource(normalizeRequiredSourceType(sourceType), sourceId);
    }

    @Transactional
    public ReviewQueueItemPO create(ReviewQueueCreateCmd cmd, Long operatorUid) {
        requireGlobalQueueAccess(operatorUid);
        ReviewQueueItemCommand command = new ReviewQueueItemCommand(
                cmd.getSourceType(),
                cmd.getSourceId(),
                cmd.getTitle(),
                cmd.getSummary(),
                cmd.getRiskLevel(),
                operatorUid,
                cmd.getPriority(),
                cmd.getExtJson(),
                cmd.getNote()
        );
        ReviewQueueItemPO created = upsertInternal(command);
        auditService.recordRequired(operatorUid, "REVIEW_QUEUE_CREATE", "REVIEW_QUEUE", created.getId(),
                null, created, clean(cmd.getNote()));
        return created;
    }

    @Override
    @Transactional
    public void upsert(ReviewQueueItemCommand command) {
        upsertInternal(command);
    }

    @Override
    @Transactional
    public void resolve(String sourceType, Long sourceId, String status, String result, String note, Long operatorUid) {
        if (operatorUid != null) {
            resolveSourceRequired(sourceType, sourceId, status, result, note, operatorUid);
            return;
        }
        try {
            resolveSourceBestEffort(sourceType, sourceId, status, result, note, null);
        } catch (RuntimeException e) {
            log.warn("review queue source resolve failed: sourceType={} sourceId={}", sourceType, sourceId, e);
        }
    }

    private void resolveSourceRequired(String sourceType, Long sourceId, String status, String result,
                                       String note, Long operatorUid) {
        SourceResolveResult resolved = resolveSourceInternal(sourceType, sourceId, status, result, note, operatorUid);
        if (resolved == null) {
            return;
        }
        auditService.recordRequired(operatorUid, "REVIEW_QUEUE_SOURCE_RESOLVE", "REVIEW_QUEUE",
                resolved.before().getId(), resolved.before(), resolved.after(), clean(note));
    }

    private void resolveSourceBestEffort(String sourceType, Long sourceId, String status, String result,
                                         String note, Long operatorUid) {
        SourceResolveResult resolved = resolveSourceInternal(sourceType, sourceId, status, result, note, operatorUid);
        if (resolved == null) {
            return;
        }
        auditService.record(operatorUid, "SYSTEM_REVIEW_QUEUE_SOURCE_RESOLVE", "REVIEW_QUEUE",
                resolved.before().getId(), resolved.before(), resolved.after(), clean(note));
    }

    private SourceResolveResult resolveSourceInternal(String sourceType, Long sourceId, String status, String result,
                                                     String note, Long operatorUid) {
        if (!queueReady() || sourceId == null || sourceId <= 0) {
            return null;
        }
        String normalizedSourceType = normalizeRequiredSourceType(sourceType);
        String normalizedStatus = normalizeResolvedStatus(status);
        ReviewQueueItemPO before = mapper.findBySource(normalizedSourceType, sourceId);
        if (before == null || !OPEN_STATUSES.contains(before.getQueueStatus())) {
            return null;
        }
        int updated = mapper.resolveBySource(normalizedSourceType, sourceId, normalizedStatus,
                clean(result), clean(note), operatorUid);
        if (updated <= 0) {
            return null;
        }
        ReviewQueueItemPO after = mapper.findBySource(normalizedSourceType, sourceId);
        return new SourceResolveResult(before, after);
    }

    private ReviewQueueItemPO upsertInternal(ReviewQueueItemCommand command) {
        requireTable();
        ReviewQueueItemPO item = new ReviewQueueItemPO();
        item.setId(idGen.nextId());
        item.setSourceType(normalizeRequiredSourceType(command.sourceType()));
        item.setSourceId(command.sourceId());
        item.setTitle(limit(required(command.title(), "title"), 200));
        item.setSummary(limit(clean(command.summary()), 1000));
        item.setRiskLevel(normalizeRiskLevel(command.riskLevel()));
        item.setQueueStatus(ReviewQueueMapper.STATUS_PENDING);
        item.setCreatorUid(command.creatorUid());
        item.setPriority(clampPriority(command.priority()));
        item.setExtJson(limit(clean(command.extJson()), 2000));
        mapper.upsertItem(item);
        ReviewQueueItemPO created = mapper.findById(item.getId());
        if (created == null && item.getSourceId() != null) {
            created = mapper.findBySource(item.getSourceType(), item.getSourceId());
        }
        if (created == null) {
            throw new BizException(ErrorCode.DATABASE_ERROR);
        }
        return created;
    }

    @Transactional
    public ReviewQueueItemPO claim(Long id, Long operatorUid) {
        ReviewQueueItemPO before = requireItem(id);
        requireCanAccessItem(operatorUid, before);
        int updated = mapper.claim(id, operatorUid);
        if (updated == 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ReviewQueueItemPO after = requireItem(id);
        auditService.recordRequired(operatorUid, "REVIEW_QUEUE_CLAIM", "REVIEW_QUEUE", id, before, after, null);
        return after;
    }

    @Transactional
    public ReviewQueueItemPO release(Long id, Long operatorUid, String note) {
        ReviewQueueItemPO before = requireItem(id);
        requireCanAccessItem(operatorUid, before);
        int updated = mapper.release(id, operatorUid);
        if (updated == 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ReviewQueueItemPO after = requireItem(id);
        auditService.recordRequired(operatorUid, "REVIEW_QUEUE_RELEASE", "REVIEW_QUEUE", id, before, after, clean(note));
        return after;
    }

    @Transactional
    public ReviewQueueItemPO approve(Long id, Long operatorUid, String note, String confirmationPhrase) {
        String auditNote = RiskConfirmation.requireCritical(note, confirmationPhrase);
        return resolve(id, operatorUid, ReviewQueueMapper.STATUS_APPROVED, "approved", auditNote, "REVIEW_QUEUE_APPROVE");
    }

    @Transactional
    public ReviewQueueItemPO reject(Long id, Long operatorUid, String note, String confirmationPhrase) {
        String auditNote = RiskConfirmation.requireCritical(note, confirmationPhrase);
        return resolve(id, operatorUid, ReviewQueueMapper.STATUS_REJECTED, "rejected", auditNote, "REVIEW_QUEUE_REJECT");
    }

    @Transactional
    public ReviewQueueItemPO close(Long id, Long operatorUid, String note, String confirmationPhrase) {
        String auditNote = RiskConfirmation.requireCritical(note, confirmationPhrase);
        return resolve(id, operatorUid, ReviewQueueMapper.STATUS_CLOSED, "closed", auditNote, "REVIEW_QUEUE_CLOSE");
    }

    private ReviewQueueItemPO resolve(Long id, Long operatorUid, String status, String result, String note, String action) {
        ReviewQueueItemPO before = requireItem(id);
        requireCanAccessItem(operatorUid, before);
        if (!OPEN_STATUSES.contains(before.getQueueStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int updated = mapper.resolve(id, status, result, clean(note), operatorUid);
        if (updated == 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ReviewQueueItemPO after = requireItem(id);
        dispatchSourceAction(after, status, result, note, operatorUid);
        auditService.recordRequired(operatorUid, action, "REVIEW_QUEUE", id, before, after, clean(note));
        return after;
    }

    private void dispatchSourceAction(ReviewQueueItemPO item, String status, String result, String note, Long operatorUid) {
        if (item == null || item.getSourceId() == null || sourceActionHandlers == null || sourceActionHandlers.isEmpty()) {
            return;
        }
        String sourceType = item.getSourceType();
        for (ReviewQueueSourceActionHandler handler : sourceActionHandlers) {
            if (handler.supports(sourceType)) {
                handler.handle(sourceType, item.getSourceId(), status, result, note, operatorUid);
            }
        }
    }

    private ReviewQueueItemPO requireItem(Long id) {
        requireTable();
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        ReviewQueueItemPO item = mapper.findById(id);
        if (item == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return item;
    }

    private void requireTable() {
        if (!queueReady()) {
            throw new BizException(ErrorCode.DATABASE_ERROR.getCode(), "Review queue table is unavailable");
        }
    }

    private boolean queueReady() {
        try {
            return migrationCheckService.reviewQueueReady();
        } catch (RuntimeException e) {
            log.warn("review queue table unavailable: {}", e.getMessage());
            return false;
        }
    }

    private void requireQueueAccess(Long operatorUid) {
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (hasGlobalReviewScope(operatorUid) || domainModeratorService.isDomainModerator(operatorUid)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    private void requireGlobalQueueAccess(Long operatorUid) {
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!hasGlobalReviewScope(operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireCanAccessItem(Long operatorUid, ReviewQueueItemPO item) {
        requireQueueAccess(operatorUid);
        if (!canAccessItem(operatorUid, item)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private boolean canAccessItem(Long operatorUid, ReviewQueueItemPO item) {
        if (hasGlobalReviewScope(operatorUid)) {
            return true;
        }
        Integer domain = resolveItemDomain(item);
        if (domain != null) {
            return domainModeratorService.canModerateDomain(operatorUid, domain);
        }
        return isCrossDomainGovernanceItem(item) && domainModeratorService.isDomainModerator(operatorUid);
    }

    private boolean isCrossDomainGovernanceItem(ReviewQueueItemPO item) {
        return item != null && SOURCE_CONTACT_REQUEST_REPORT.equalsIgnoreCase(item.getSourceType());
    }

    private Integer resolveItemDomain(ReviewQueueItemPO item) {
        if (item == null || sourceDomainResolvers == null || sourceDomainResolvers.isEmpty()) {
            return null;
        }
        for (ReviewQueueSourceDomainResolver resolver : sourceDomainResolvers) {
            if (!resolver.supports(item.getSourceType())) {
                continue;
            }
            try {
                Integer domain = resolver.resolveDomain(item.getSourceType(), item.getSourceId());
                if (domain != null) {
                    return domain;
                }
            } catch (RuntimeException e) {
                log.warn("review queue source domain resolve failed: sourceType={} sourceId={}",
                        item.getSourceType(), item.getSourceId(), e);
            }
        }
        return null;
    }

    private AccessibleScanResult listAccessibleItems(Long operatorUid,
                                                     String status,
                                                     String sourceType,
                                                     String riskLevel,
                                                     int requestedLimit) {
        List<ReviewQueueItemPO> accessible = new ArrayList<>(Math.min(requestedLimit, MAX_LIMIT));
        Integer beforePriority = null;
        java.time.LocalDateTime beforeCreateTime = null;
        Long beforeId = null;
        int scannedRows = 0;
        boolean scanLimited = false;

        while (accessible.size() < requestedLimit && scannedRows < MAX_ACCESSIBLE_SCAN_ROWS) {
            int remainingScanRows = MAX_ACCESSIBLE_SCAN_ROWS - scannedRows;
            int batchSize = Math.min(ACCESSIBLE_SCAN_BATCH_SIZE, remainingScanRows);
            List<ReviewQueueItemPO> rows = mapper.listAfter(status, sourceType, riskLevel,
                    beforePriority, beforeCreateTime, beforeId, batchSize);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            scannedRows += rows.size();
            for (ReviewQueueItemPO item : rows) {
                if (canAccessItem(operatorUid, item)) {
                    accessible.add(item);
                    if (accessible.size() >= requestedLimit) {
                        break;
                    }
                }
            }
            ReviewQueueItemPO last = rows.get(rows.size() - 1);
            beforePriority = last.getPriority() == null ? 0 : last.getPriority();
            beforeCreateTime = last.getCreateTime();
            beforeId = last.getId();
            if (rows.size() < batchSize || beforeCreateTime == null || beforeId == null) {
                break;
            }
        }
        if (accessible.size() < requestedLimit && scannedRows >= MAX_ACCESSIBLE_SCAN_ROWS) {
            scanLimited = true;
            log.warn("review queue accessible scan reached limit, operatorUid={}, status={}, sourceType={}, riskLevel={}, requestedLimit={}, scannedRows={}",
                    operatorUid, status, sourceType, riskLevel, requestedLimit, scannedRows);
        }
        return new AccessibleScanResult(accessible, scannedRows, scanLimited);
    }

    private boolean hasGlobalReviewScope(Long operatorUid) {
        return operatorUid != null && (adminPermissionService.isAdmin(operatorUid)
                || adminPermissionService.hasRole(operatorUid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode());
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private static int clampPriority(Integer priority) {
        if (priority == null) {
            return 0;
        }
        return Math.max(0, Math.min(priority, 100));
    }

    private static String normalizeStatus(String value) {
        String status = clean(value);
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "pending", "claimed", "approved", "rejected", "closed" -> status.toLowerCase(Locale.ROOT);
            default -> null;
        };
    }

    private static String normalizeRiskLevel(String value) {
        String risk = clean(value);
        if (!StringUtils.hasText(risk)) {
            return "medium";
        }
        return switch (risk.toLowerCase(Locale.ROOT)) {
            case "critical", "high", "medium", "low" -> risk.toLowerCase(Locale.ROOT);
            default -> "medium";
        };
    }

    private static String normalizeResolvedStatus(String value) {
        String status = clean(value);
        if (!StringUtils.hasText(status)) {
            return ReviewQueueMapper.STATUS_CLOSED;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approved" -> ReviewQueueMapper.STATUS_APPROVED;
            case "rejected" -> ReviewQueueMapper.STATUS_REJECTED;
            case "closed" -> ReviewQueueMapper.STATUS_CLOSED;
            default -> ReviewQueueMapper.STATUS_CLOSED;
        };
    }

    private static String normalizeSourceType(String value) {
        String sourceType = clean(value);
        return StringUtils.hasText(sourceType) ? sourceType.toUpperCase(Locale.ROOT) : null;
    }

    private static String normalizeRequiredSourceType(String value) {
        String sourceType = normalizeSourceType(value);
        if (!StringUtils.hasText(sourceType)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return limit(sourceType, 32);
    }

    private static String required(String value, String field) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), field + " is required");
        }
        return cleaned;
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private record SourceResolveResult(ReviewQueueItemPO before, ReviewQueueItemPO after) {
    }

    private record AccessibleScanResult(List<ReviewQueueItemPO> items, int scannedRows, boolean scanLimited) {
    }
}
