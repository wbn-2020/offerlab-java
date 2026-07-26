package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.api.KnowledgeMaintenanceReadFacade;
import com.offerlab.community.post.api.dto.KnowledgeMaintenanceSourceDTO;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.ContentMaintenanceTaskRow;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationRow;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceMapper;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.ReferenceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostKnowledgeMaintenanceReadService implements KnowledgeMaintenanceReadFacade {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int POST_BATCH_SIZE = 500;

    private final PostReferenceMapper referenceMapper;
    private final PostKnowledgeRelationMapper relationMapper;
    private final ContentMaintenanceTaskMapper maintenanceTaskMapper;
    private final PostRepository postRepository;
    private final DomainModeratorService domainModeratorService;

    @Override
    public List<KnowledgeMaintenanceSourceDTO> listActions(Long uid, int limit) {
        return listActions(uid, null, null, null, null, null, limit);
    }

    @Override
    public List<KnowledgeMaintenanceSourceDTO> listActions(Long uid,
                                                           String actionType,
                                                           String status,
                                                           java.time.LocalDateTime cursorTime,
                                                           Integer cursorSourceOrder,
                                                           Long cursorSourceId,
                                                           int limit) {
        requireUid(uid);
        int effectiveLimit = normalizeLimit(limit);
        List<ReferenceRow> references = include(actionType, "REFERENCE_REVIEW")
                && include(status, "BROKEN")
                ? safe(referenceMapper.listBrokenOwnedAfter(
                uid,
                cursorTime,
                cursorIdForSource(KnowledgeMaintenanceReadFacade.SOURCE_ORDER_REFERENCE,
                        cursorTime, cursorSourceOrder, cursorSourceId),
                effectiveLimit))
                : List.of();
        List<PostKnowledgeRelationRow> ownedRelations = include(actionType, "RELATION_REVIEW")
                ? safe(relationMapper.listOwnedActionsAfter(
                uid,
                status,
                cursorTime,
                cursorIdForSource(KnowledgeMaintenanceReadFacade.SOURCE_ORDER_RELATION_PROPOSAL,
                        cursorTime, cursorSourceOrder, cursorSourceId),
                effectiveLimit))
                : List.of();
        List<Integer> moderatedDomains = include(actionType, "RELATION_REVIEW")
                && include(status, "PENDING")
                ? domainModeratorService.listModeratableDomains(uid)
                : List.of();
        List<PostKnowledgeRelationRow> pendingReviews = moderatedDomains.isEmpty()
                ? List.of()
                : safe(relationMapper.listPendingReviewActionsForDomainsAfter(
                uid,
                moderatedDomains,
                status,
                cursorTime,
                cursorIdForSource(KnowledgeMaintenanceReadFacade.SOURCE_ORDER_RELATION_REVIEW,
                        cursorTime, cursorSourceOrder, cursorSourceId),
                effectiveLimit));
        List<ContentMaintenanceTaskRow> maintenanceTasks = include(actionType, "MAINTENANCE_TASK")
                ? safe(maintenanceTaskMapper.listKnowledgeActionsAfter(
                uid,
                status,
                cursorTime,
                cursorIdForSource(KnowledgeMaintenanceReadFacade.SOURCE_ORDER_MAINTENANCE_TASK,
                        cursorTime, cursorSourceOrder, cursorSourceId),
                effectiveLimit))
                : List.of();
        Map<Long, Post> posts = batchPosts(references, ownedRelations, pendingReviews);
        List<SourceCandidate> items = new ArrayList<>();
        collectBrokenReferences(uid, references, posts, items);
        collectOwnedRelations(uid, ownedRelations, posts, items);
        collectRelationReviews(pendingReviews, posts, items);
        collectMaintenanceTasks(maintenanceTasks, items);
        return items.stream()
                .sorted(SOURCE_ORDER)
                .limit(effectiveLimit)
                .map(SourceCandidate::item)
                .toList();
    }

    @Override
    public long countActions(Long uid, String actionType, String status) {
        requireUid(uid);
        long total = 0L;
        if (include(actionType, "REFERENCE_REVIEW") && include(status, "BROKEN")) {
            total += Math.max(0L, referenceMapper.countBrokenOwned(uid));
        }
        if (include(actionType, "RELATION_REVIEW")) {
            total += Math.max(0L, relationMapper.countOwnedActions(uid, status));
            if (include(status, "PENDING")) {
                List<Integer> domains = domainModeratorService.listModeratableDomains(uid);
                if (!domains.isEmpty()) {
                    total += Math.max(0L,
                            relationMapper.countPendingReviewActionsForDomains(uid, domains, status));
                }
            }
        }
        if (include(actionType, "MAINTENANCE_TASK")) {
            total += Math.max(0L, maintenanceTaskMapper.countKnowledgeActions(uid, status));
        }
        return total;
    }

    @Override
    public Map<String, Long> countActionsByType(Long uid) {
        requireUid(uid);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("REFERENCE_REVIEW", Math.max(0L, referenceMapper.countBrokenOwned(uid)));
        long relationCount = Math.max(0L, relationMapper.countOwnedActions(uid, null));
        List<Integer> domains = domainModeratorService.listModeratableDomains(uid);
        if (!domains.isEmpty()) {
            relationCount += Math.max(0L,
                    relationMapper.countPendingReviewActionsForDomains(uid, domains, null));
        }
        counts.put("RELATION_REVIEW", relationCount);
        counts.put("MAINTENANCE_TASK",
                Math.max(0L, maintenanceTaskMapper.countKnowledgeActions(uid, null)));
        return Map.copyOf(counts);
    }

    private void collectBrokenReferences(Long uid, List<ReferenceRow> rows,
                                         Map<Long, Post> posts,
                                         List<SourceCandidate> items) {
        for (ReferenceRow row : rows) {
            Post post = posts.get(row.getPostId());
            if (post == null || !Objects.equals(post.getAuthorId(), uid)) {
                continue;
            }
            KnowledgeMaintenanceSourceDTO item = KnowledgeMaintenanceSourceDTO.builder()
                    .sourceKey("REFERENCE:" + row.getId())
                    .actionType("REFERENCE_REVIEW")
                    .title(title(post, row.getTitle()))
                    .reason("BROKEN_REFERENCE_REQUIRES_REVIEW")
                    .status(row.getReferenceStatus())
                    .priority("HIGH")
                    .canonicalRoute("/editor/" + row.getPostId() + "#references")
                    .postId(row.getPostId())
                    .updatedAt(row.getUpdateTime())
                    .build();
            items.add(new SourceCandidate(
                    item,
                    KnowledgeMaintenanceReadFacade.SOURCE_ORDER_REFERENCE,
                    row.getId()));
        }
    }

    private void collectOwnedRelations(Long uid, List<PostKnowledgeRelationRow> rows,
                                       Map<Long, Post> posts,
                                       List<SourceCandidate> items) {
        for (PostKnowledgeRelationRow row : rows) {
            Post source = posts.get(row.getSourcePostId());
            boolean ownsSource = source != null && Objects.equals(source.getAuthorId(), uid);
            if (!ownsSource && !Objects.equals(row.getProposerUid(), uid)) {
                continue;
            }
            KnowledgeMaintenanceSourceDTO item = KnowledgeMaintenanceSourceDTO.builder()
                    .sourceKey("RELATION_PROPOSAL:" + row.getId())
                    .actionType("RELATION_REVIEW")
                    .title(title(source, "Knowledge relation " + row.getRelationType()))
                    .reason("PENDING".equals(row.getReviewStatus())
                            ? "RELATION_REVIEW_PENDING" : "RELATION_REVIEW_REJECTED")
                    .status(row.getReviewStatus())
                    .priority("REJECTED".equals(row.getReviewStatus()) ? "HIGH" : "MEDIUM")
                    .canonicalRoute("/post/" + row.getSourcePostId() + "#content-evolution")
                    .postId(row.getSourcePostId())
                    .updatedAt(row.getUpdateTime())
                    .build();
            items.add(new SourceCandidate(
                    item,
                    KnowledgeMaintenanceReadFacade.SOURCE_ORDER_RELATION_PROPOSAL,
                    row.getId()));
        }
    }

    private void collectRelationReviews(List<PostKnowledgeRelationRow> rows,
                                        Map<Long, Post> posts,
                                        List<SourceCandidate> items) {
        for (PostKnowledgeRelationRow row : rows) {
            Post source = posts.get(row.getSourcePostId());
            if (source == null) {
                continue;
            }
            KnowledgeMaintenanceSourceDTO item = KnowledgeMaintenanceSourceDTO.builder()
                    .sourceKey("RELATION_REVIEW:" + row.getId())
                    .actionType("RELATION_REVIEW")
                    .title(title(source, "Review knowledge relation"))
                    .reason("RELATION_REVIEW_REQUIRED")
                    .status(row.getReviewStatus())
                    .priority(row.getRiskLevel())
                    .canonicalRoute("/admin/collaboration?tab=knowledge-relations")
                    .postId(row.getSourcePostId())
                    .updatedAt(row.getUpdateTime())
                    .build();
            items.add(new SourceCandidate(
                    item,
                    KnowledgeMaintenanceReadFacade.SOURCE_ORDER_RELATION_REVIEW,
                    row.getId()));
        }
    }

    private void collectMaintenanceTasks(List<ContentMaintenanceTaskRow> rows,
                                         List<SourceCandidate> items) {
        for (ContentMaintenanceTaskRow row : rows) {
            KnowledgeMaintenanceSourceDTO item = KnowledgeMaintenanceSourceDTO.builder()
                    .sourceKey("MAINTENANCE_TASK:" + row.getId())
                    .actionType("MAINTENANCE_TASK")
                    .title(StringUtils.hasText(row.getTitle()) ? row.getTitle() : "Content maintenance task")
                    .reason("CONTENT_MAINTENANCE_TASK_ACTIVE")
                    .status(row.getStatus())
                    .priority("SUBMITTED".equals(row.getStatus()) ? "MEDIUM" : "HIGH")
                    .canonicalRoute("/me/maintenance")
                    .postId(row.getSourcePostId())
                    .updatedAt(row.getUpdateTime())
                    .build();
            items.add(new SourceCandidate(
                    item,
                    KnowledgeMaintenanceReadFacade.SOURCE_ORDER_MAINTENANCE_TASK,
                    row.getId()));
        }
    }

    private Map<Long, Post> batchPosts(List<ReferenceRow> references,
                                       List<PostKnowledgeRelationRow> ownedRelations,
                                       List<PostKnowledgeRelationRow> pendingReviews) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        references.stream().map(ReferenceRow::getPostId).filter(Objects::nonNull).forEach(ids::add);
        ownedRelations.stream().map(PostKnowledgeRelationRow::getSourcePostId)
                .filter(Objects::nonNull).forEach(ids::add);
        pendingReviews.stream().map(PostKnowledgeRelationRow::getSourcePostId)
                .filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> postIds = List.copyOf(ids);
        Map<Long, Post> posts = new HashMap<>();
        for (int start = 0; start < postIds.size(); start += POST_BATCH_SIZE) {
            int end = Math.min(start + POST_BATCH_SIZE, postIds.size());
            Map<Long, Post> batch = postRepository.batchFindByIds(postIds.subList(start, end));
            if (batch != null && !batch.isEmpty()) {
                posts.putAll(batch);
            }
        }
        return posts;
    }

    private static String title(Post post, String fallback) {
        return post != null && StringUtils.hasText(post.getTitle()) ? post.getTitle() : fallback;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean include(String requested, String actual) {
        return requested == null || requested.equalsIgnoreCase(actual);
    }

    private static Long cursorIdForSource(int sourceOrder,
                                          java.time.LocalDateTime cursorTime,
                                          Integer cursorSourceOrder,
                                          Long cursorSourceId) {
        if (cursorTime == null) {
            return null;
        }
        if (cursorSourceOrder == null || cursorSourceId == null || cursorSourceId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "knowledge action cursor is invalid");
        }
        if (sourceOrder < cursorSourceOrder) {
            return 0L;
        }
        if (sourceOrder > cursorSourceOrder) {
            return Long.MAX_VALUE;
        }
        return cursorSourceId;
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static final Comparator<SourceCandidate> SOURCE_ORDER = Comparator
            .comparing((SourceCandidate candidate) -> candidate.item().getUpdatedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparingInt(SourceCandidate::sourceOrder)
            .thenComparing(SourceCandidate::sourceId, Comparator.reverseOrder());

    private record SourceCandidate(KnowledgeMaintenanceSourceDTO item,
                                   int sourceOrder,
                                   Long sourceId) {
    }
}
