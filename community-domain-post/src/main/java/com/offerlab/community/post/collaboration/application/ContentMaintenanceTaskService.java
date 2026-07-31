package com.offerlab.community.post.collaboration.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.CommunityRoleAccessService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceCandidateDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskCreateCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskDTO;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReassignCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskReviewCmd;
import com.offerlab.community.post.collaboration.api.ContentMaintenanceTaskSubmitCmd;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.SeriesRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import com.offerlab.community.post.domain.model.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ContentMaintenanceTaskService {

    private static final String MIGRATION = "db/migration/20260715_content_maintenance_stage8.sql";
    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> SOURCE_TYPES = Set.of(
            "CHANNEL_HEALTH", "SEARCH_GAP", "SUGGESTION", "FRESHNESS",
            "PROFILE_CONFIRMATION", "QUESTION", "MANUAL");
    private static final Set<String> STATUSES = Set.of(
            "OPEN", "CLAIMED", "SUBMITTED", "COMPLETED", "CLOSED");

    private final ContentMaintenanceTaskMapper mapper;
    private final SnowflakeIdGenerator idGenerator;
    private final DomainModeratorService domainModeratorService;
    private final CommunityRoleAccessService communityRoleAccessService;
    private final AdminAuditService adminAuditService;
    private final PostFacade postFacade;
    private final CollaborationMapper collaborationMapper;

    @Transactional
    public ContentMaintenanceTaskDTO create(ContentMaintenanceTaskCreateCmd cmd, Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        int domain = requireDomain(cmd.getDomain());
        requireModerate(operatorUid, domain);
        Long assigneeUid = requireId(cmd.getAssigneeUid());
        if (mapper.userExists(assigneeUid) <= 0) throw new BizException(ErrorCode.USER_NOT_FOUND);
        requireMaintenanceRole(assigneeUid, domain);
        Long sourcePostId = positiveOrNull(cmd.getSourcePostId());
        if (sourcePostId != null) requirePublicPostInDomain(sourcePostId, domain);
        Long id = idGenerator.nextId();
        mapper.insert(id, domain, sourceType(cmd.getSourceType()), positiveOrNull(cmd.getSourceRefId()),
                sourcePostId, operatorUid, assigneeUid, required(cmd.getTitle(), 160),
                required(cmd.getDetail(), 2000));
        ContentMaintenanceTaskRow created = requireTask(mapper.lockById(id));
        adminAuditService.recordRequired(operatorUid, "CONTENT_MAINTENANCE_TASK_CREATE",
                "CONTENT_MAINTENANCE_TASK", id, null, created, "maintenance task created");
        return toDto(created, operatorUid);
    }

    public PageResult<ContentMaintenanceTaskDTO> listMine(Long uid, String requestedStatus, long cursor, int size) {
        requireTable();
        requireId(uid);
        return page(mapper.listMine(uid, status(requestedStatus), safeCursor(cursor), pageSize(size) + 1),
                pageSize(size), row -> toDto(row, uid));
    }

    public PageResult<ContentMaintenanceTaskDTO> listQueue(Integer requestedDomain, String requestedStatus,
                                                            Long operatorUid, long cursor, int size) {
        requireTable();
        Integer domain = requestedDomain == null ? null : requireDomain(requestedDomain);
        List<Integer> moderatedDomains = moderatedDomains(operatorUid);
        if (domain != null && !moderatedDomains.contains(domain)) throw new BizException(ErrorCode.FORBIDDEN);
        int safeSize = pageSize(size);
        String normalizedStatus = status(requestedStatus);
        List<ContentMaintenanceTaskRow> rows;
        if (domain != null || moderatedDomains.size() == 5) {
            rows = mapper.listQueue(domain, normalizedStatus, safeCursor(cursor), safeSize + 1);
        } else {
            rows = moderatedDomains.stream()
                    .flatMap(item -> mapper.listQueue(item, normalizedStatus, safeCursor(cursor), safeSize + 1).stream())
                    .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                    .limit(safeSize + 1L)
                    .toList();
        }
        return page(rows, safeSize, row -> toDto(row, operatorUid));
    }

    public PageResult<ContentMaintenanceCandidateDTO> listCandidates(
            Long uid,
            Integer requestedDomain,
            String requestedSourceType,
            Integer contentType,
            long cursor,
            int size) {
        requireTable();
        requireId(uid);
        if (contentType != null && !Post.isSupportedType(contentType)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String normalizedSourceType = StringUtils.hasText(requestedSourceType)
                ? sourceType(requestedSourceType)
                : null;
        List<Integer> allowedDomains = maintenanceDomains(uid);
        Integer domain = requestedDomain == null ? null : requireDomain(requestedDomain);
        if (domain != null && !allowedDomains.contains(domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int safeSize = pageSize(size);
        List<ContentMaintenanceTaskRow> rows;
        if (domain != null) {
            rows = mapper.listCandidates(uid, domain, normalizedSourceType, contentType,
                    safeCursor(cursor), safeSize + 1);
        } else {
            rows = allowedDomains.stream()
                    .flatMap(item -> mapper.listCandidates(uid, item, normalizedSourceType, contentType,
                            safeCursor(cursor), safeSize + 1).stream())
                    .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                    .limit(safeSize + 1L)
                    .toList();
        }
        return candidatePage(rows, safeSize, uid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO claim(Long id, Long uid) {
        requireTable();
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        if (!uid.equals(before.getAssigneeUid())) throw new BizException(ErrorCode.FORBIDDEN);
        requireMaintenanceRole(uid, before.getDomain());
        if (mapper.claim(before.getId(), uid) != 1 && !"CLAIMED".equals(before.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return toDto(requireTask(mapper.lockById(before.getId())), uid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO reassign(
            Long id,
            ContentMaintenanceTaskReassignCmd cmd,
            Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        requireModerate(operatorUid, before.getDomain());
        if (!Set.of("OPEN", "CLAIMED").contains(before.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        Long replacementUid = requireId(cmd.getReplacementUid());
        if (replacementUid.equals(before.getAssigneeUid())) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        if (mapper.userExists(replacementUid) <= 0) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        requireMaintenanceRole(replacementUid, before.getDomain());
        String reason = required(cmd.getReason(), 500);
        if (mapper.reassign(before.getId(), replacementUid) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ContentMaintenanceTaskRow after = requireTask(mapper.lockById(before.getId()));
        adminAuditService.recordRequired(operatorUid, "CONTENT_MAINTENANCE_TASK_REASSIGN",
                "CONTENT_MAINTENANCE_TASK", before.getId(), before, after, reason);
        return toDto(after, operatorUid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO submit(Long id, ContentMaintenanceTaskSubmitCmd cmd, Long uid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        if (!uid.equals(before.getAssigneeUid())) throw new BizException(ErrorCode.FORBIDDEN);
        requireMaintenanceRole(uid, before.getDomain());
        Delivery delivery = delivery(cmd, before.getDomain(), uid);
        if (mapper.submit(before.getId(), uid, delivery.type(), delivery.id(), delivery.postId(),
                required(cmd.getNote(), 1000)) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return toDto(requireTask(mapper.lockById(before.getId())), uid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO review(Long id, ContentMaintenanceTaskReviewCmd cmd, Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        requireModerate(operatorUid, before.getDomain());
        String note = required(cmd.getNote(), 1000);
        String decision = enumValue(cmd.getDecision(), Set.of("APPROVED", "REJECTED"));
        int updated = "APPROVED".equals(decision)
                ? mapper.approve(before.getId(), operatorUid, note)
                : mapper.reject(before.getId(), operatorUid, note);
        if (updated != 1) throw new BizException(ErrorCode.INVALID_STATUS);
        ContentMaintenanceTaskRow after = requireTask(mapper.lockById(before.getId()));
        adminAuditService.recordRequired(operatorUid, "CONTENT_MAINTENANCE_TASK_" + decision,
                "CONTENT_MAINTENANCE_TASK", before.getId(), before, after, note);
        return toDto(after, operatorUid);
    }

    @Transactional
    public ContentMaintenanceTaskDTO close(Long id, ContentMaintenanceTaskReviewCmd cmd, Long operatorUid) {
        requireTable();
        if (cmd == null) throw new BizException(ErrorCode.PARAM_ERROR);
        ContentMaintenanceTaskRow before = requireTask(mapper.lockById(requireId(id)));
        requireModerate(operatorUid, before.getDomain());
        String note = required(cmd.getNote(), 1000);
        if (mapper.close(before.getId(), operatorUid, note) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ContentMaintenanceTaskRow after = requireTask(mapper.lockById(before.getId()));
        adminAuditService.recordRequired(operatorUid, "CONTENT_MAINTENANCE_TASK_CLOSE",
                "CONTENT_MAINTENANCE_TASK", before.getId(), before, after, note);
        return toDto(after, operatorUid);
    }

    private Delivery delivery(ContentMaintenanceTaskSubmitCmd cmd, Integer domain, Long uid) {
        String type = enumValue(cmd.getDeliveryType(), Set.of("POST", "QUESTION", "SERIES"));
        Long id = requireId(cmd.getDeliveryRefId());
        Long postId = positiveOrNull(cmd.getDeliveryPostId());
        if ("SERIES".equals(type)) {
            if (postId != null) throw new BizException(ErrorCode.PARAM_ERROR);
            SeriesRow series = collaborationMapper.selectSeries(id, uid);
            if (series == null
                    || !domain.equals(series.getDomain())
                    || series.getPostCount() == null
                    || series.getPostCount() <= 0
                    || collaborationMapper.seriesHasContributor(id, uid) <= 0) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            return new Delivery(type, id, null);
        }
        if (postId == null) postId = id;
        if (!id.equals(postId)) throw new BizException(ErrorCode.PARAM_ERROR);
        PostDTO post = postFacade.getPostForAuthor(postId, uid);
        if (post == null || !isPublic(post) || !domain.equals(post.getDomain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if ("QUESTION".equals(type) && !Integer.valueOf(Post.TYPE_COMMUNITY_QUESTION).equals(post.getPostType())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return new Delivery(type, id, postId);
    }

    private void requirePublicPostInDomain(Long postId, Integer domain) {
        PostDTO post = postFacade.getPost(postId);
        if (post == null || !isPublic(post) || !domain.equals(post.getDomain())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static boolean isPublic(PostDTO post) {
        return Integer.valueOf(Post.STATUS_PUBLISHED).equals(post.getPostStatus())
                && Integer.valueOf(Post.VIS_PUBLIC).equals(post.getVisibility());
    }

    private ContentMaintenanceTaskDTO toDto(ContentMaintenanceTaskRow row, Long viewerUid) {
        boolean moderator = viewerUid != null && canModerate(viewerUid, row.getDomain());
        boolean assignee = viewerUid != null && viewerUid.equals(row.getAssigneeUid());
        boolean activeMaintainer = assignee && hasMaintenanceRole(viewerUid, row.getDomain());
        return ContentMaintenanceTaskDTO.builder()
                .id(row.getId()).domain(row.getDomain()).sourceType(row.getSourceType())
                .sourceRefId(row.getSourceRefId()).sourcePostId(row.getSourcePostId())
                .createdByUid(row.getCreatedByUid()).assigneeUid(row.getAssigneeUid())
                .title(row.getTitle()).detail(row.getDetail()).status(row.getStatus())
                .deliveryType(row.getDeliveryType()).deliveryRefId(row.getDeliveryRefId())
                .deliveryPostId(row.getDeliveryPostId()).deliveryNote(row.getDeliveryNote())
                .reviewNote(row.getReviewNote())
                .canClaim(activeMaintainer && "OPEN".equals(row.getStatus()))
                .canSubmit(activeMaintainer && "CLAIMED".equals(row.getStatus()))
                .canReview(moderator && "SUBMITTED".equals(row.getStatus()))
                .canClose(moderator && Set.of("OPEN", "CLAIMED", "SUBMITTED").contains(row.getStatus()))
                .canReassign(moderator && Set.of("OPEN", "CLAIMED").contains(row.getStatus()))
                .claimedAt(row.getClaimedAt()).submittedAt(row.getSubmittedAt())
                .reviewedByUid(row.getReviewedByUid()).reviewedAt(row.getReviewedAt())
                .closedByUid(row.getClosedByUid()).closedAt(row.getClosedAt())
                .createTime(row.getCreateTime()).updateTime(row.getUpdateTime())
                .build();
    }

    private boolean canModerate(Long uid, Integer domain) {
        return uid != null && domain != null && domainModeratorService.canModerateDomain(uid, domain);
    }

    private void requireModerate(Long uid, Integer domain) {
        if (uid == null || !canModerate(uid, domain)) throw new BizException(ErrorCode.FORBIDDEN);
    }

    private List<Integer> moderatedDomains(Long uid) {
        if (uid == null) throw new BizException(ErrorCode.FORBIDDEN);
        List<Integer> domains = List.of(1, 2, 3, 4, 5).stream()
                .filter(domain -> canModerate(uid, domain))
                .toList();
        if (domains.isEmpty()) throw new BizException(ErrorCode.FORBIDDEN);
        return domains;
    }

    private List<Integer> maintenanceDomains(Long uid) {
        List<Integer> domains = List.of(1, 2, 3, 4, 5).stream()
                .filter(domain -> hasMaintenanceRole(uid, domain))
                .toList();
        if (domains.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return domains;
    }

    private boolean hasMaintenanceRole(Long uid, Integer domain) {
        return uid != null
                && domain != null
                && communityRoleAccessService.hasActiveGrant(
                uid, "CHANNEL_RESOURCE_MAINTAINER", domainCode(domain));
    }

    private void requireMaintenanceRole(Long uid, Integer domain) {
        if (!hasMaintenanceRole(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireTable() {
        try {
            if (mapper.tableExists() > 0) return;
        } catch (RuntimeException ignored) {
            // Convert metadata failures to the public dependency contract.
        }
        throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                "Content maintenance migration is required: " + MIGRATION);
    }

    private static ContentMaintenanceTaskRow requireTask(ContentMaintenanceTaskRow row) {
        if (row == null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        return row;
    }

    private static String sourceType(String value) {
        return enumValue(value, SOURCE_TYPES);
    }

    private static String status(String value) {
        return StringUtils.hasText(value) ? enumValue(value, STATUSES) : null;
    }

    private static String enumValue(String value, Set<String> allowed) {
        String normalized = required(value, 32).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BizException(ErrorCode.PARAM_ERROR);
        return normalized;
    }

    private static int requireDomain(Integer domain) {
        if (domain == null || domain < 1 || domain > 5) throw new BizException(ErrorCode.PARAM_ERROR);
        return domain;
    }

    private static Long requireId(Long value) {
        if (value == null || value <= 0) throw new BizException(ErrorCode.PARAM_ERROR);
        return value;
    }

    private static Long positiveOrNull(Long value) {
        return value == null ? null : requireId(value);
    }

    private static String required(String value, int max) {
        if (!StringUtils.hasText(value)) throw new BizException(ErrorCode.PARAM_ERROR);
        String normalized = value.trim();
        if (normalized.length() > max) throw new BizException(ErrorCode.PARAM_ERROR);
        return normalized;
    }

    private static int pageSize(int value) {
        return Math.max(1, Math.min(value <= 0 ? 20 : value, MAX_PAGE_SIZE));
    }

    private static long safeCursor(long cursor) {
        return Math.max(0, cursor);
    }

    private static PageResult<ContentMaintenanceTaskDTO> page(List<ContentMaintenanceTaskRow> rows, int size,
                                                               Function<ContentMaintenanceTaskRow, ContentMaintenanceTaskDTO> converter) {
        List<ContentMaintenanceTaskRow> safe = rows == null ? List.of() : rows;
        boolean hasMore = safe.size() > size;
        List<ContentMaintenanceTaskRow> visible = safe.stream().limit(size).toList();
        String next = hasMore && !visible.isEmpty() ? String.valueOf(visible.get(visible.size() - 1).getId()) : null;
        return PageResult.of(visible.stream().map(converter).toList(), next, hasMore);
    }

    private static PageResult<ContentMaintenanceCandidateDTO> candidatePage(
            List<ContentMaintenanceTaskRow> rows,
            int size,
            Long uid) {
        List<ContentMaintenanceTaskRow> safe = rows == null ? List.of() : rows;
        boolean hasMore = safe.size() > size;
        List<ContentMaintenanceTaskRow> visible = safe.stream().limit(size).toList();
        String next = hasMore && !visible.isEmpty()
                ? String.valueOf(visible.get(visible.size() - 1).getId())
                : null;
        List<ContentMaintenanceCandidateDTO> items = visible.stream()
                .map(row -> {
                    boolean assignedToViewer = uid.equals(row.getAssigneeUid());
                    return ContentMaintenanceCandidateDTO.builder()
                            .id(row.getId())
                            .domain(row.getDomain())
                            .sourceType(row.getSourceType())
                            .sourceRefId(row.getSourceRefId())
                            .sourcePostId(row.getSourcePostId())
                            .sourcePostType(row.getSourcePostType())
                            .title(row.getTitle())
                            .status(row.getStatus())
                            .assignmentStatus(assignedToViewer ? "ASSIGNED_TO_ME" : "UNASSIGNED")
                            .canClaim(assignedToViewer)
                            .createTime(row.getCreateTime())
                            .updateTime(row.getUpdateTime())
                            .build();
                })
                .toList();
        return PageResult.of(items, next, hasMore);
    }

    private static String domainCode(Integer domain) {
        return switch (domain == null ? 0 : domain) {
            case 1 -> "TECH";
            case 2 -> "CAREER";
            case 3 -> "READING";
            case 4 -> "LIFESTYLE";
            case 5 -> "INVESTMENT";
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        };
    }

    private record Delivery(String type, Long id, Long postId) {
    }
}
