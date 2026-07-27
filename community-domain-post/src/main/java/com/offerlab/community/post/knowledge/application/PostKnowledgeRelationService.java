package com.offerlab.community.post.knowledge.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueuePublisher;
import com.offerlab.community.post.api.dto.KnowledgeRiskLevel;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.knowledge.api.KnowledgeThreadDTO;
import com.offerlab.community.post.knowledge.api.KnowledgeThreadNodeDTO;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationCreateCmd;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationDTO;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationReviewStatus;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationType;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationVisibility;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostKnowledgeRelationService {

    public static final String REVIEW_SOURCE_TYPE = "KNOWLEDGE_RELATION";

    private static final int MAX_PUBLIC_LIMIT = 100;
    private static final int MAX_REASON_LENGTH = 2000;
    private static final int MAX_REVIEW_NOTE_LENGTH = 1000;
    // Reading-thread bounds: a linear timeline, never an unbounded graph walk.
    static final int MAX_THREAD_HOPS = 3;
    static final int MAX_THREAD_NODES = 15;
    private static final int THREAD_RELATIONS_PER_HOP = 50;

    private final PostKnowledgeRelationMapper mapper;
    private final PostRepository postRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final ReviewQueuePublisher reviewQueuePublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public PostKnowledgeRelationDTO propose(Long sourcePostId,
                                            PostKnowledgeRelationCreateCmd cmd,
                                            Long proposerUid) {
        requireUser(proposerUid);
        if (cmd == null || cmd.getRelationType() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!StringUtils.hasText(cmd.getReasonText())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "Knowledge relation reason is required");
        }
        String reasonText = cmd.getReasonText().trim();
        if (reasonText.length() > MAX_REASON_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "Knowledge relation reason is too long");
        }
        long requestedSourceId = requireId(sourcePostId);
        long requestedTargetId = requireId(cmd.getTargetPostId());
        if (requestedSourceId == requestedTargetId) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "A post cannot have a knowledge relation to itself");
        }

        Post requestedSource = requirePost(requestedSourceId);
        Post requestedTarget = requirePost(requestedTargetId);
        if (!requestedSource.isVisibleTo(null, false)
                && !proposerUid.equals(requestedSource.getAuthorId())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "Knowledge relation source must be public or owned by the proposer");
        }
        if (!requestedTarget.isVisibleTo(null, false)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "Knowledge relation target must be a public published post");
        }

        NormalizedPair pair = normalizePair(requestedSource, requestedTarget, cmd.getRelationType());
        if (!pair.target().isVisibleTo(null, false)) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "Normalized knowledge relation target must be a public published post");
        }
        String relationType = cmd.getRelationType().name();
        requireNoCycle(pair.source().getId(), pair.target().getId(), cmd.getRelationType(), null);
        if (mapper.findEffective(pair.source().getId(), pair.target().getId(), relationType) != null) {
            throw duplicateRelation();
        }

        PostKnowledgeRelationRow row = new PostKnowledgeRelationRow();
        row.setId(idGenerator.nextId());
        row.setSourcePostId(pair.source().getId());
        row.setTargetPostId(pair.target().getId());
        row.setRelationType(relationType);
        row.setReasonText(reasonText);
        row.setProposerUid(proposerUid);
        row.setReviewStatus(PostKnowledgeRelationReviewStatus.PENDING.name());
        row.setVisibilityStatus(PostKnowledgeRelationVisibility.VISIBLE.name());
        row.setRiskLevel(riskLevel(cmd.getRelationType()).name());
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw duplicateRelation();
        }

        publishReviewItem(row, pair.source(), pair.target());
        PostKnowledgeRelationRow persisted = mapper.findActiveById(row.getId());
        return toDto(persisted == null ? row : persisted);
    }

    public List<PostKnowledgeRelationDTO> listPublic(Long postId, int limit) {
        long normalizedPostId = requireId(postId);
        Post post = requirePost(normalizedPostId);
        if (!post.isVisibleTo(null, false)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return mapper.listPublicByPostId(normalizedPostId, clampLimit(limit)).stream()
                .map(PostKnowledgeRelationService::toDto)
                .toList();
    }

    /**
     * Bounded read-only reading thread. Upstream follows "anchor has a
     * prerequisite" links backwards ("read these first"); downstream follows
     * continuation/supersession links forwards ("read these next"). Every hop
     * reuses the public relation filter (APPROVED + VISIBLE + both posts
     * publicly visible), so nothing pending, rejected, or hidden can appear.
     */
    public KnowledgeThreadDTO readingThread(Long postId) {
        long anchorId = requireId(postId);
        Post anchor = requirePost(anchorId);
        if (!anchor.isVisibleTo(null, false)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Set<Long> visited = new HashSet<>();
        visited.add(anchorId);
        boolean[] truncated = {false};
        List<KnowledgeThreadNodeDTO> upstream =
                walk(anchorId, visited, truncated, true);
        Collections.reverse(upstream);
        List<KnowledgeThreadNodeDTO> downstream =
                walk(anchorId, visited, truncated, false);
        return KnowledgeThreadDTO.builder()
                .anchorPostId(anchorId)
                .upstream(upstream)
                .downstream(downstream)
                .truncated(truncated[0])
                .build();
    }

    private List<KnowledgeThreadNodeDTO> walk(long anchorId,
                                              Set<Long> visited,
                                              boolean[] truncated,
                                              boolean upstream) {
        List<KnowledgeThreadNodeDTO> nodes = new ArrayList<>();
        long currentId = anchorId;
        for (int hop = 1; hop <= MAX_THREAD_HOPS; hop++) {
            ThreadSelection selection = nextStep(currentId, visited, upstream);
            if (selection.truncated()) {
                truncated[0] = true;
            }
            Optional<ThreadStep> next = selection.step();
            if (next.isEmpty()) {
                return nodes;
            }
            if (visited.size() >= MAX_THREAD_NODES) {
                truncated[0] = true;
                return nodes;
            }
            ThreadStep step = next.get();
            visited.add(step.postId());
            nodes.add(KnowledgeThreadNodeDTO.builder()
                    .postId(step.postId())
                    .title(step.post().getTitle())
                    .domain(step.post().getDomain())
                    .relationType(step.relationType())
                    .hop(hop)
                    .build());
            currentId = step.postId();
        }
        // Ran out of hop budget; report truncation only if the chain continues.
        ThreadSelection continuation = nextStep(currentId, visited, upstream);
        if (continuation.truncated() || continuation.step().isPresent()) {
            truncated[0] = true;
        }
        return nodes;
    }

    /**
     * Picks one deterministic chain neighbor of {@code fromId}. The mapper
     * applies public visibility, chain type, and walk direction before LIMIT.
     * More than one distinct neighbor is a branch: the newest edge is followed
     * and the thread is marked truncated because the alternatives are omitted.
     */
    private ThreadSelection nextStep(long fromId, Set<Long> visited, boolean upstream) {
        List<PostKnowledgeRelationRow> rows = mapper.listPublicChainByPostId(
                fromId, upstream, THREAD_RELATIONS_PER_HOP + 1);
        List<PostKnowledgeRelationRow> safeRows = rows == null ? List.of() : rows;
        boolean queryTruncated = safeRows.size() > THREAD_RELATIONS_PER_HOP;
        Map<Long, ThreadStep> candidates = new LinkedHashMap<>();
        for (PostKnowledgeRelationRow row : safeRows.stream()
                .limit(THREAD_RELATIONS_PER_HOP)
                .toList()) {
            PostKnowledgeRelationType type = parseType(row.getRelationType());
            if (type == null) {
                continue;
            }
            Long neighborId = chainNeighbor(row, type, fromId, upstream);
            if (neighborId == null || visited.contains(neighborId)) {
                continue;
            }
            if (candidates.containsKey(neighborId)) {
                continue;
            }
            Optional<Post> neighbor = postRepository.findById(neighborId)
                    .filter(post -> post.isVisibleTo(null, false));
            if (neighbor.isEmpty()) {
                continue;
            }
            candidates.put(neighborId, new ThreadStep(neighborId, neighbor.get(), type));
        }
        return new ThreadSelection(
                candidates.values().stream().findFirst(),
                queryTruncated || candidates.size() > 1);
    }

    /**
     * Chain semantics per relation row {@code source -[type]-> target}:
     * PREREQUISITE_OF: source must be read before target.
     * CONTINUES / SUPERSEDES: source is the newer piece following target.
     * Everything else (SUPPLEMENTS, CONTRADICTS, DUPLICATE_OF) is lateral and
     * never joins the reading chain.
     */
    private static Long chainNeighbor(PostKnowledgeRelationRow row,
                                      PostKnowledgeRelationType type,
                                      long fromId,
                                      boolean upstream) {
        long sourceId = row.getSourcePostId();
        long targetId = row.getTargetPostId();
        boolean fromIsSource = fromId == sourceId;
        boolean fromIsTarget = fromId == targetId;
        if (!fromIsSource && !fromIsTarget) {
            return null;
        }
        if (type == PostKnowledgeRelationType.PREREQUISITE_OF) {
            // Reading order: source → target.
            if (upstream && fromIsTarget) {
                return sourceId;
            }
            if (!upstream && fromIsSource) {
                return targetId;
            }
            return null;
        }
        if (type == PostKnowledgeRelationType.CONTINUES
                || type == PostKnowledgeRelationType.SUPERSEDES) {
            // Reading order: target → source (source is the follow-up).
            if (upstream && fromIsSource) {
                return targetId;
            }
            if (!upstream && fromIsTarget) {
                return sourceId;
            }
            return null;
        }
        return null;
    }

    private static PostKnowledgeRelationType parseType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return PostKnowledgeRelationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record ThreadStep(long postId, Post post, PostKnowledgeRelationType relationType) {
    }

    private record ThreadSelection(Optional<ThreadStep> step, boolean truncated) {
    }

    @Transactional
    public PostKnowledgeRelationDTO reviewFromQueue(Long relationId,
                                                    Long reviewerUid,
                                                    boolean approved,
                                                    String note) {
        requireUser(reviewerUid);
        PostKnowledgeRelationRow current = mapper.findActiveById(requireId(relationId));
        if (current == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (reviewerUid.equals(current.getProposerUid())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    "Knowledge relation proposers cannot review their own proposal");
        }
        if (!PostKnowledgeRelationReviewStatus.PENDING.name().equals(current.getReviewStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        String reviewStatus = approved
                ? PostKnowledgeRelationReviewStatus.APPROVED.name()
                : PostKnowledgeRelationReviewStatus.REJECTED.name();
        if (approved) {
            Post source = requirePost(current.getSourcePostId());
            Post target = requirePost(current.getTargetPostId());
            if (!source.isVisibleTo(null, false) || !target.isVisibleTo(null, false)) {
                throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                        "Approved knowledge relations require two public published posts");
            }
            requireNoCycle(source.getId(), target.getId(),
                    PostKnowledgeRelationType.valueOf(current.getRelationType()), current.getId());
        }
        if (mapper.reviewPending(current.getId(), reviewStatus, reviewerUid,
                cleanReviewNote(note)) != 1) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        PostKnowledgeRelationRow reviewed = mapper.findActiveById(current.getId());
        return toDto(reviewed == null ? current : reviewed);
    }

    private void publishReviewItem(PostKnowledgeRelationRow row, Post source, Post target) {
        reviewQueuePublisher.upsert(new ReviewQueueItemCommand(
                REVIEW_SOURCE_TYPE,
                row.getId(),
                "Knowledge relation: " + row.getRelationType(),
                row.getReasonText(),
                row.getRiskLevel().toLowerCase(Locale.ROOT),
                row.getProposerUid(),
                reviewPriority(row.getRiskLevel()),
                reviewExtJson(row, source, target),
                "Post knowledge relation proposed"
        ));
    }

    private String reviewExtJson(PostKnowledgeRelationRow row, Post source, Post target) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "sourcePostId", row.getSourcePostId(),
                    "sourceTitle", safeTitle(source),
                    "targetPostId", row.getTargetPostId(),
                    "targetTitle", safeTitle(target),
                    "relationType", row.getRelationType()
            ));
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "Knowledge relation review payload serialization failed");
        }
    }

    private static NormalizedPair normalizePair(Post source,
                                                Post target,
                                                PostKnowledgeRelationType relationType) {
        if (relationType == PostKnowledgeRelationType.CONTRADICTS
                && source.getId() > target.getId()) {
            return new NormalizedPair(target, source);
        }
        return new NormalizedPair(source, target);
    }

    private void requireNoCycle(Long sourcePostId,
                                Long targetPostId,
                                PostKnowledgeRelationType relationType,
                                Long excludeId) {
        if (!requiresAcyclicDirection(relationType)) {
            return;
        }
        if (mapper.countEffectivePath(targetPostId, sourcePostId, relationType.name(), excludeId) > 0) {
            throw new BizException(ErrorCode.INVALID_STATUS.getCode(),
                    "Knowledge relation would create a directional cycle");
        }
    }

    private static boolean requiresAcyclicDirection(PostKnowledgeRelationType relationType) {
        return relationType == PostKnowledgeRelationType.DUPLICATE_OF
                || relationType == PostKnowledgeRelationType.SUPERSEDES
                || relationType == PostKnowledgeRelationType.CONTINUES
                || relationType == PostKnowledgeRelationType.PREREQUISITE_OF;
    }

    private Post requirePost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BizException(ErrorCode.POST_NOT_FOUND));
    }

    private static long requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return id;
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 20 : limit, MAX_PUBLIC_LIMIT));
    }

    private static String cleanReviewNote(String note) {
        if (!StringUtils.hasText(note)) {
            return "Reviewed in review queue";
        }
        String trimmed = note.trim();
        return trimmed.length() <= MAX_REVIEW_NOTE_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_REVIEW_NOTE_LENGTH);
    }

    private static KnowledgeRiskLevel riskLevel(PostKnowledgeRelationType relationType) {
        return switch (relationType) {
            case DUPLICATE_OF, SUPERSEDES, CONTRADICTS -> KnowledgeRiskLevel.HIGH;
            case PREREQUISITE_OF -> KnowledgeRiskLevel.MEDIUM;
            case CONTINUES, SUPPLEMENTS -> KnowledgeRiskLevel.LOW;
        };
    }

    private static int reviewPriority(String riskLevel) {
        return switch (KnowledgeRiskLevel.valueOf(riskLevel)) {
            case HIGH -> 80;
            case MEDIUM -> 60;
            case LOW -> 40;
        };
    }

    private static String safeTitle(Post post) {
        return post == null || !StringUtils.hasText(post.getTitle())
                ? "Post " + (post == null ? "" : post.getId())
                : post.getTitle().trim();
    }

    private static BizException duplicateRelation() {
        return new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                "An effective knowledge relation already exists");
    }

    private static PostKnowledgeRelationDTO toDto(PostKnowledgeRelationRow row) {
        return PostKnowledgeRelationDTO.builder()
                .id(row.getId())
                .sourcePostId(row.getSourcePostId())
                .targetPostId(row.getTargetPostId())
                .relationType(PostKnowledgeRelationType.valueOf(row.getRelationType()))
                .reasonText(row.getReasonText())
                .reviewStatus(PostKnowledgeRelationReviewStatus.valueOf(row.getReviewStatus()))
                .visibilityStatus(PostKnowledgeRelationVisibility.valueOf(row.getVisibilityStatus()))
                .riskLevel(KnowledgeRiskLevel.valueOf(row.getRiskLevel()))
                .createdAt(row.getCreateTime())
                .build();
    }

    private record NormalizedPair(Post source, Post target) {
    }
}
