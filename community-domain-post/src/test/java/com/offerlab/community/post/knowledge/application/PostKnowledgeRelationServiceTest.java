package com.offerlab.community.post.knowledge.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueuePublisher;
import com.offerlab.community.post.api.dto.KnowledgeRiskLevel;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationCreateCmd;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationDTO;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationReviewStatus;
import com.offerlab.community.post.knowledge.api.PostKnowledgeRelationType;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostKnowledgeRelationServiceTest {

    @Test
    void contradictsNormalizesAsUnorderedAndKeepsOneEffectiveRelation() {
        MapperStub mapper = new MapperStub();
        PostRepositoryStub posts = new PostRepositoryStub(
                publicPost(20L, 1),
                publicPost(10L, 2));
        ReviewQueueStub queue = new ReviewQueueStub();
        PostKnowledgeRelationService service = service(mapper, posts, queue);

        PostKnowledgeRelationDTO created = service.propose(
                20L,
                new PostKnowledgeRelationCreateCmd(10L, PostKnowledgeRelationType.CONTRADICTS,
                        "The conclusions conflict"),
                7L);

        assertEquals(10L, created.getSourcePostId());
        assertEquals(20L, created.getTargetPostId());
        assertEquals(KnowledgeRiskLevel.HIGH, created.getRiskLevel());
        assertEquals(PostKnowledgeRelationService.REVIEW_SOURCE_TYPE, queue.last.sourceType());
        assertTrue(queue.last.extJson().contains("\"sourcePostId\":10"));
        assertTrue(queue.last.extJson().contains("\"targetPostId\":20"));

        BizException duplicate = assertThrows(BizException.class, () -> service.propose(
                10L,
                new PostKnowledgeRelationCreateCmd(20L, PostKnowledgeRelationType.CONTRADICTS,
                        "Same unordered pair"),
                8L));

        assertEquals(ErrorCode.DUPLICATE_OPERATION.getCode(), duplicate.getCode());
        assertEquals(1, mapper.rows.size());
    }

    @Test
    void proposalRejectsSelfRelationAndNonPublicTarget() {
        MapperStub mapper = new MapperStub();
        PostRepositoryStub posts = new PostRepositoryStub(
                publicPost(10L, 1),
                privatePost(20L, 1));
        PostKnowledgeRelationService service = service(mapper, posts, new ReviewQueueStub());

        BizException self = assertThrows(BizException.class, () -> service.propose(
                10L,
                new PostKnowledgeRelationCreateCmd(10L, PostKnowledgeRelationType.SUPPLEMENTS, "same"),
                7L));
        BizException privateTarget = assertThrows(BizException.class, () -> service.propose(
                10L,
                new PostKnowledgeRelationCreateCmd(20L, PostKnowledgeRelationType.SUPPLEMENTS, "private"),
                7L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), self.getCode());
        assertEquals(ErrorCode.INVALID_STATUS.getCode(), privateTarget.getCode());
        assertTrue(mapper.rows.isEmpty());
    }

    @Test
    void queueHandlerRejectsProposerSelfReviewAndApprovesForAnotherReviewer() {
        MapperStub mapper = new MapperStub();
        PostRepositoryStub posts = new PostRepositoryStub(
                publicPost(10L, 3),
                publicPost(20L, 3));
        PostKnowledgeRelationService service = service(mapper, posts, new ReviewQueueStub());
        PostKnowledgeRelationDTO proposed = service.propose(
                10L,
                new PostKnowledgeRelationCreateCmd(20L, PostKnowledgeRelationType.PREREQUISITE_OF,
                        "Read this first"),
                7L);
        PostKnowledgeRelationQueueActionHandler handler =
                new PostKnowledgeRelationQueueActionHandler(service);

        BizException selfReview = assertThrows(BizException.class, () -> handler.handle(
                "KNOWLEDGE_RELATION", proposed.getId(), "approved", "approved", "looks good", 7L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), selfReview.getCode());
        assertEquals(PostKnowledgeRelationReviewStatus.PENDING,
                PostKnowledgeRelationReviewStatus.valueOf(
                        mapper.findActiveById(proposed.getId()).getReviewStatus()));
        assertTrue(service.listPublic(10L, 20).isEmpty());

        handler.handle("knowledge_relation", proposed.getId(), "APPROVED", "approved", "verified", 8L);

        PostKnowledgeRelationRow reviewed = mapper.findActiveById(proposed.getId());
        assertEquals(PostKnowledgeRelationReviewStatus.APPROVED.name(), reviewed.getReviewStatus());
        assertEquals(8L, reviewed.getReviewerUid());
        assertEquals("verified", reviewed.getReviewNote());
        assertEquals(1, service.listPublic(10L, 20).size());
    }

    @Test
    void sourceDomainResolverUsesCanonicalSourcePost() {
        MapperStub mapper = new MapperStub();
        PostRepositoryStub posts = new PostRepositoryStub(
                publicPost(20L, 1),
                publicPost(10L, 4));
        PostKnowledgeRelationService service = service(mapper, posts, new ReviewQueueStub());
        PostKnowledgeRelationDTO proposed = service.propose(
                20L,
                new PostKnowledgeRelationCreateCmd(10L, PostKnowledgeRelationType.CONTRADICTS,
                        "Different conclusions"),
                7L);
        PostKnowledgeRelationReviewQueueSourceDomainResolver resolver =
                new PostKnowledgeRelationReviewQueueSourceDomainResolver(mapper, posts);

        assertTrue(resolver.supports("knowledge_relation"));
        assertEquals(4, resolver.resolveDomain("KNOWLEDGE_RELATION", proposed.getId()));
    }

    private static PostKnowledgeRelationService service(MapperStub mapper,
                                                        PostRepository posts,
                                                        ReviewQueuePublisher queue) {
        return new PostKnowledgeRelationService(
                mapper,
                posts,
                new SnowflakeIdGenerator(),
                queue,
                new ObjectMapper());
    }

    private static Post publicPost(Long id, Integer domain) {
        return Post.builder()
                .id(id)
                .title("Post " + id)
                .domain(domain)
                .postStatus(Post.STATUS_PUBLISHED)
                .visibility(Post.VIS_PUBLIC)
                .contentEnvironment(Post.CONTENT_ENVIRONMENT_COMMUNITY)
                .build();
    }

    private static Post privatePost(Long id, Integer domain) {
        return Post.builder()
                .id(id)
                .title("Private " + id)
                .domain(domain)
                .postStatus(Post.STATUS_PUBLISHED)
                .visibility(Post.VIS_SELF)
                .contentEnvironment(Post.CONTENT_ENVIRONMENT_COMMUNITY)
                .build();
    }

    private static final class MapperStub implements PostKnowledgeRelationMapper {
        private final Map<Long, PostKnowledgeRelationRow> rows = new LinkedHashMap<>();

        @Override
        public int insert(PostKnowledgeRelationRow row) {
            row.setCreateTime(LocalDateTime.now());
            rows.put(row.getId(), copy(row));
            return 1;
        }

        @Override
        public PostKnowledgeRelationRow findActiveById(Long id) {
            PostKnowledgeRelationRow row = rows.get(id);
            return row == null ? null : copy(row);
        }

        @Override
        public PostKnowledgeRelationRow findEffective(Long sourcePostId,
                                                      Long targetPostId,
                                                      String relationType) {
            return rows.values().stream()
                    .filter(row -> sourcePostId.equals(row.getSourcePostId()))
                    .filter(row -> targetPostId.equals(row.getTargetPostId()))
                    .filter(row -> relationType.equals(row.getRelationType()))
                    .filter(row -> List.of("PENDING", "APPROVED").contains(row.getReviewStatus()))
                    .findFirst()
                    .map(PostKnowledgeRelationServiceTest::copy)
                    .orElse(null);
        }

        @Override
        public List<PostKnowledgeRelationRow> listOwnedActions(Long uid, int limit) {
            return rows.values().stream()
                    .filter(row -> uid.equals(row.getProposerUid()))
                    .filter(row -> List.of("PENDING", "REJECTED").contains(row.getReviewStatus()))
                    .limit(limit)
                    .map(PostKnowledgeRelationServiceTest::copy)
                    .toList();
        }

        @Override
        public List<PostKnowledgeRelationRow> listOwnedActionsAfter(
                Long uid, String status, LocalDateTime cursorTime, Long cursorId, int limit) {
            return rows.values().stream()
                    .filter(row -> uid.equals(row.getProposerUid()))
                    .filter(row -> List.of("PENDING", "REJECTED").contains(row.getReviewStatus()))
                    .filter(row -> status == null || status.equals(row.getReviewStatus()))
                    .filter(row -> afterCursor(row, cursorTime, cursorId))
                    .limit(limit)
                    .map(PostKnowledgeRelationServiceTest::copy)
                    .toList();
        }

        @Override
        public long countOwnedActions(Long uid, String status) {
            return rows.values().stream()
                    .filter(row -> uid.equals(row.getProposerUid()))
                    .filter(row -> List.of("PENDING", "REJECTED").contains(row.getReviewStatus()))
                    .filter(row -> status == null || status.equals(row.getReviewStatus()))
                    .count();
        }

        @Override
        public List<PostKnowledgeRelationRow> listPendingReviewActions(int limit) {
            return rows.values().stream()
                    .filter(row -> "PENDING".equals(row.getReviewStatus()))
                    .limit(limit)
                    .map(PostKnowledgeRelationServiceTest::copy)
                    .toList();
        }

        @Override
        public List<PostKnowledgeRelationRow> listPendingReviewActionsForDomainsAfter(
                Long uid, List<Integer> domains, String status,
                LocalDateTime cursorTime, Long cursorId, int limit) {
            return rows.values().stream()
                    .filter(row -> "PENDING".equals(row.getReviewStatus()))
                    .filter(row -> !uid.equals(row.getProposerUid()))
                    .filter(row -> status == null || status.equals(row.getReviewStatus()))
                    .filter(row -> afterCursor(row, cursorTime, cursorId))
                    .limit(limit)
                    .map(PostKnowledgeRelationServiceTest::copy)
                    .toList();
        }

        @Override
        public long countPendingReviewActionsForDomains(
                Long uid, List<Integer> domains, String status) {
            return rows.values().stream()
                    .filter(row -> "PENDING".equals(row.getReviewStatus()))
                    .filter(row -> !uid.equals(row.getProposerUid()))
                    .filter(row -> status == null || status.equals(row.getReviewStatus()))
                    .count();
        }

        @Override
        public int countEffectivePath(Long startPostId, Long targetPostId,
                                      String relationType, Long excludeId) {
            java.util.Set<Long> visited = new java.util.HashSet<>();
            java.util.ArrayDeque<Long> pending = new java.util.ArrayDeque<>();
            pending.add(startPostId);
            while (!pending.isEmpty()) {
                Long current = pending.removeFirst();
                if (!visited.add(current)) {
                    continue;
                }
                for (PostKnowledgeRelationRow row : rows.values()) {
                    if (java.util.Objects.equals(row.getId(), excludeId)
                            || !current.equals(row.getSourcePostId())
                            || !relationType.equals(row.getRelationType())
                            || !List.of("PENDING", "APPROVED").contains(row.getReviewStatus())) {
                        continue;
                    }
                    if (targetPostId.equals(row.getTargetPostId())) {
                        return 1;
                    }
                    pending.add(row.getTargetPostId());
                }
            }
            return 0;
        }

        @Override
        public List<PostKnowledgeRelationRow> listPublicByPostId(Long postId, int limit) {
            return rows.values().stream()
                    .filter(row -> postId.equals(row.getSourcePostId()) || postId.equals(row.getTargetPostId()))
                    .filter(row -> "APPROVED".equals(row.getReviewStatus()))
                    .filter(row -> "VISIBLE".equals(row.getVisibilityStatus()))
                    .limit(limit)
                    .map(PostKnowledgeRelationServiceTest::copy)
                    .toList();
        }

        @Override
        public List<PostKnowledgeRelationRow> listPublicChainByPostId(
                Long postId, boolean upstream, int limit) {
            return listPublicByPostId(postId, Integer.MAX_VALUE).stream()
                    .filter(row -> List.of("PREREQUISITE_OF", "CONTINUES", "SUPERSEDES")
                            .contains(row.getRelationType()))
                    .filter(row -> chainDirectionMatches(row, postId, upstream))
                    .limit(limit)
                    .toList();
        }

        @Override
        public int reviewPending(Long id, String reviewStatus, Long reviewerUid, String reviewNote) {
            PostKnowledgeRelationRow row = rows.get(id);
            if (row == null || !"PENDING".equals(row.getReviewStatus())
                    || reviewerUid.equals(row.getProposerUid())) {
                return 0;
            }
            row.setReviewStatus(reviewStatus);
            row.setReviewerUid(reviewerUid);
            row.setReviewNote(reviewNote);
            row.setReviewedAt(LocalDateTime.now());
            return 1;
        }

        private static boolean chainDirectionMatches(PostKnowledgeRelationRow row,
                                                     Long postId,
                                                     boolean upstream) {
            if ("PREREQUISITE_OF".equals(row.getRelationType())) {
                return upstream
                        ? postId.equals(row.getTargetPostId())
                        : postId.equals(row.getSourcePostId());
            }
            return upstream
                    ? postId.equals(row.getSourcePostId())
                    : postId.equals(row.getTargetPostId());
        }

        private static boolean afterCursor(PostKnowledgeRelationRow row,
                                           LocalDateTime cursorTime,
                                           Long cursorId) {
            if (cursorTime == null) {
                return true;
            }
            int timeCompare = row.getUpdateTime().compareTo(cursorTime);
            return timeCompare < 0
                    || (timeCompare == 0 && row.getId() < cursorId);
        }
    }

    private static final class PostRepositoryStub implements PostRepository {
        private final Map<Long, Post> posts = new LinkedHashMap<>();

        private PostRepositoryStub(Post... posts) {
            for (Post post : posts) {
                this.posts.put(post.getId(), post);
            }
        }

        @Override
        public void save(Post post) {
            posts.put(post.getId(), post);
        }

        @Override
        public Optional<Post> findById(Long id) {
            return Optional.ofNullable(posts.get(id));
        }

        @Override
        public Map<Long, Post> batchFindByIds(Collection<Long> ids) {
            Map<Long, Post> result = new LinkedHashMap<>();
            ids.forEach(id -> {
                if (posts.containsKey(id)) {
                    result.put(id, posts.get(id));
                }
            });
            return result;
        }

        @Override
        public boolean update(Post post) {
            posts.put(post.getId(), post);
            return true;
        }

        @Override
        public boolean updateStatusIfCurrent(Long postId, Integer expectedStatus,
                                             Integer nextStatus, Integer expectedVersion) {
            return false;
        }

        @Override
        public void softDelete(Long id) {
            posts.remove(id);
        }

        @Override
        public List<Post> findByAuthor(Long authorId, long cursor, int size) {
            return List.of();
        }

        @Override
        public List<Post> findLatest(long cursor, int size) {
            return List.of();
        }

        @Override
        public List<Post> findPosts(Long authorId, Long tagId, Integer postType,
                                    Boolean featured, Integer domain, long cursor, int size) {
            return List.of();
        }

        @Override
        public List<Post> findPostsByKeyset(Long authorId, Long tagId, Integer postType,
                                            Boolean featured, Integer domain,
                                            LocalDateTime cursorTime, Long cursorId, int size) {
            return List.of();
        }
    }

    private static final class ReviewQueueStub implements ReviewQueuePublisher {
        private ReviewQueueItemCommand last;

        @Override
        public void upsert(ReviewQueueItemCommand command) {
            last = command;
        }

        @Override
        public void resolve(String sourceType, Long sourceId, String status,
                            String result, String note, Long operatorUid) {
        }
    }

    private static PostKnowledgeRelationRow copy(PostKnowledgeRelationRow source) {
        PostKnowledgeRelationRow copy = new PostKnowledgeRelationRow();
        copy.setId(source.getId());
        copy.setSourcePostId(source.getSourcePostId());
        copy.setTargetPostId(source.getTargetPostId());
        copy.setRelationType(source.getRelationType());
        copy.setReasonText(source.getReasonText());
        copy.setProposerUid(source.getProposerUid());
        copy.setReviewStatus(source.getReviewStatus());
        copy.setVisibilityStatus(source.getVisibilityStatus());
        copy.setReviewerUid(source.getReviewerUid());
        copy.setReviewNote(source.getReviewNote());
        copy.setReviewedAt(source.getReviewedAt());
        copy.setRiskLevel(source.getRiskLevel());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setIsDeleted(source.getIsDeleted());
        return copy;
    }
}
