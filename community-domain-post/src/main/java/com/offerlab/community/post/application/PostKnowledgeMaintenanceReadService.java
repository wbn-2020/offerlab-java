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
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostKnowledgeMaintenanceReadService implements KnowledgeMaintenanceReadFacade {

    private final PostReferenceMapper referenceMapper;
    private final PostKnowledgeRelationMapper relationMapper;
    private final ContentMaintenanceTaskMapper maintenanceTaskMapper;
    private final PostRepository postRepository;
    private final DomainModeratorService domainModeratorService;

    @Override
    public List<KnowledgeMaintenanceSourceDTO> listActions(Long uid, int limit) {
        requireUid(uid);
        List<KnowledgeMaintenanceSourceDTO> items = new ArrayList<>();
        collectBrokenReferences(uid, items);
        collectOwnedRelations(uid, items);
        collectRelationReviews(uid, items);
        collectMaintenanceTasks(uid, items);
        return items.stream()
                .sorted(Comparator
                        .comparing(KnowledgeMaintenanceSourceDTO::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(KnowledgeMaintenanceSourceDTO::getSourceKey,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private void collectBrokenReferences(Long uid, List<KnowledgeMaintenanceSourceDTO> items) {
        for (ReferenceRow row : safe(referenceMapper.listBrokenOwned(uid, 0))) {
            Post post = ownedPost(row.getPostId(), uid);
            if (post == null) {
                continue;
            }
            items.add(KnowledgeMaintenanceSourceDTO.builder()
                    .sourceKey("REFERENCE:" + row.getId())
                    .actionType("REFERENCE_REVIEW")
                    .title(title(post, row.getTitle()))
                    .reason("BROKEN_REFERENCE_REQUIRES_REVIEW")
                    .status(row.getReferenceStatus())
                    .priority("HIGH")
                    .canonicalRoute("/editor/" + row.getPostId() + "#references")
                    .postId(row.getPostId())
                    .updatedAt(row.getUpdateTime())
                    .build());
        }
    }

    private void collectOwnedRelations(Long uid, List<KnowledgeMaintenanceSourceDTO> items) {
        for (PostKnowledgeRelationRow row : safe(relationMapper.listOwnedActions(uid, 0))) {
            Post source = ownedPost(row.getSourcePostId(), uid);
            if (source == null && !Objects.equals(row.getProposerUid(), uid)) {
                continue;
            }
            items.add(KnowledgeMaintenanceSourceDTO.builder()
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
                    .build());
        }
    }

    private void collectRelationReviews(Long uid, List<KnowledgeMaintenanceSourceDTO> items) {
        for (PostKnowledgeRelationRow row : safe(relationMapper.listPendingReviewActions(0))) {
            if (Objects.equals(row.getProposerUid(), uid)) {
                continue;
            }
            Post source = postRepository.findById(row.getSourcePostId()).orElse(null);
            if (source == null || !domainModeratorService.canModerateDomain(uid, source.getDomain())) {
                continue;
            }
            items.add(KnowledgeMaintenanceSourceDTO.builder()
                    .sourceKey("RELATION_REVIEW:" + row.getId())
                    .actionType("RELATION_REVIEW")
                    .title(title(source, "Review knowledge relation"))
                    .reason("RELATION_REVIEW_REQUIRED")
                    .status(row.getReviewStatus())
                    .priority(row.getRiskLevel())
                    .canonicalRoute("/admin/collaboration?tab=knowledge-relations")
                    .postId(row.getSourcePostId())
                    .updatedAt(row.getUpdateTime())
                    .build());
        }
    }

    private void collectMaintenanceTasks(Long uid, List<KnowledgeMaintenanceSourceDTO> items) {
        for (ContentMaintenanceTaskRow row : safe(maintenanceTaskMapper.listKnowledgeActions(uid, 0))) {
            items.add(KnowledgeMaintenanceSourceDTO.builder()
                    .sourceKey("MAINTENANCE_TASK:" + row.getId())
                    .actionType("MAINTENANCE_TASK")
                    .title(StringUtils.hasText(row.getTitle()) ? row.getTitle() : "Content maintenance task")
                    .reason("CONTENT_MAINTENANCE_TASK_ACTIVE")
                    .status(row.getStatus())
                    .priority("SUBMITTED".equals(row.getStatus()) ? "MEDIUM" : "HIGH")
                    .canonicalRoute("/me/maintenance")
                    .postId(row.getSourcePostId())
                    .updatedAt(row.getUpdateTime())
                    .build());
        }
    }

    private Post ownedPost(Long postId, Long uid) {
        if (postId == null) {
            return null;
        }
        return postRepository.findById(postId)
                .filter(post -> Objects.equals(post.getAuthorId(), uid))
                .orElse(null);
    }

    private static String title(Post post, String fallback) {
        return post != null && StringUtils.hasText(post.getTitle()) ? post.getTitle() : fallback;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static void requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }
}
