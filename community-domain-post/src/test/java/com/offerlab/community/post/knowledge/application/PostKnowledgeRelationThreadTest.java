package com.offerlab.community.post.knowledge.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.knowledge.api.KnowledgeThreadDTO;
import com.offerlab.community.post.knowledge.api.KnowledgeThreadNodeDTO;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationMapper;
import com.offerlab.community.post.knowledge.infrastructure.PostKnowledgeRelationRow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostKnowledgeRelationThreadTest {

    @Test
    void continuationChainSplitsIntoUpstreamAndDownstreamAroundTheAnchor() {
        // Reading order A → B → C → D, expressed as "later CONTINUES earlier".
        Harness h = new Harness(publicPost(1L), publicPost(2L), publicPost(3L), publicPost(4L));
        h.approved(2L, 1L, "CONTINUES");
        h.approved(3L, 2L, "CONTINUES");
        h.approved(4L, 3L, "CONTINUES");

        KnowledgeThreadDTO thread = h.service().readingThread(2L);

        assertEquals(List.of(1L), postIds(thread.getUpstream()));
        assertEquals(List.of(3L, 4L), postIds(thread.getDownstream()));
        assertEquals(1, thread.getUpstream().get(0).getHop());
        assertEquals(2, thread.getDownstream().get(1).getHop());
        assertFalse(thread.isTruncated());
    }

    @Test
    void multiHopUpstreamIsReturnedInActualReadingOrder() {
        Harness h = new Harness(publicPost(1L), publicPost(2L), publicPost(3L), publicPost(4L));
        h.approved(2L, 1L, "CONTINUES");
        h.approved(3L, 2L, "CONTINUES");
        h.approved(4L, 3L, "CONTINUES");

        KnowledgeThreadDTO thread = h.service().readingThread(4L);

        assertEquals(List.of(1L, 2L, 3L), postIds(thread.getUpstream()));
        assertEquals(List.of(3, 2, 1),
                thread.getUpstream().stream().map(KnowledgeThreadNodeDTO::getHop).toList(),
                "hop stays the distance from the anchor while list order follows reading order");
        assertFalse(thread.isTruncated());
    }

    @Test
    void prerequisiteAndSupersessionFollowReadingOrder() {
        Harness h = new Harness(publicPost(1L), publicPost(2L), publicPost(3L));
        // 1 must be read before 2; 3 supersedes 2.
        h.approved(1L, 2L, "PREREQUISITE_OF");
        h.approved(3L, 2L, "SUPERSEDES");

        KnowledgeThreadDTO thread = h.service().readingThread(2L);

        assertEquals(List.of(1L), postIds(thread.getUpstream()));
        assertEquals(List.of(3L), postIds(thread.getDownstream()));
    }

    @Test
    void cyclesTerminateAndNeverRepeatANode() {
        Harness h = new Harness(publicPost(1L), publicPost(2L));
        h.approved(2L, 1L, "CONTINUES");
        h.approved(1L, 2L, "CONTINUES");

        KnowledgeThreadDTO thread = h.service().readingThread(1L);

        List<Long> all = new ArrayList<>(postIds(thread.getUpstream()));
        all.addAll(postIds(thread.getDownstream()));
        assertEquals(1, all.size(), "the other node appears exactly once: " + all);
        assertEquals(2L, all.get(0));
    }

    @Test
    void pendingRelationsAndHiddenPostsStayOutOfTheThread() {
        Harness h = new Harness(publicPost(1L), publicPost(2L), privatePost(3L), publicPost(4L));
        h.relation(2L, 1L, "CONTINUES", "PENDING", "VISIBLE");
        h.approved(3L, 1L, "CONTINUES");
        h.relation(4L, 1L, "CONTINUES", "APPROVED", "HIDDEN");

        KnowledgeThreadDTO thread = h.service().readingThread(1L);

        assertTrue(thread.getUpstream().isEmpty());
        assertTrue(thread.getDownstream().isEmpty(),
                "pending relation, private post, and hidden relation must all be excluded");
    }

    @Test
    void lateralRelationTypesNeverJoinTheChain() {
        Harness h = new Harness(publicPost(1L), publicPost(2L), publicPost(3L), publicPost(4L));
        h.approved(2L, 1L, "SUPPLEMENTS");
        h.approved(3L, 1L, "CONTRADICTS");
        h.approved(4L, 1L, "DUPLICATE_OF");

        KnowledgeThreadDTO thread = h.service().readingThread(1L);

        assertTrue(thread.getUpstream().isEmpty());
        assertTrue(thread.getDownstream().isEmpty());
    }

    @Test
    void lateralNoiseBeyondThePerHopLimitCannotHideAChainNeighbor() {
        Harness h = new Harness(publicPost(1L), publicPost(2L), publicPost(3L));
        // The chain edge is older than more than one full relation page of
        // lateral edges. SQL must filter relation types before applying LIMIT.
        h.approved(2L, 1L, "CONTINUES");
        for (int i = 0; i < 60; i++) {
            h.approved(3L, 1L, "SUPPLEMENTS");
        }

        KnowledgeThreadDTO thread = h.service().readingThread(1L);

        assertEquals(List.of(2L), postIds(thread.getDownstream()));
        assertFalse(thread.isTruncated(), "lateral noise is not part of the bounded chain query");
    }

    @Test
    void branchSelectsOneDeterministicPathAndMarksThreadTruncated() {
        Harness h = new Harness(publicPost(1L), publicPost(2L), publicPost(3L));
        h.approved(2L, 1L, "CONTINUES");
        h.approved(3L, 1L, "CONTINUES");

        KnowledgeThreadDTO thread = h.service().readingThread(1L);

        assertEquals(List.of(3L), postIds(thread.getDownstream()),
                "the newest approved edge is the deterministic branch choice");
        assertTrue(thread.isTruncated(), "omitted branch alternatives must be reported");
    }

    @Test
    void relationQueryLimitUsesASentinelAndMarksThreadTruncated() {
        Harness h = new Harness(publicPost(1L), publicPost(2L));
        for (int i = 0; i < 51; i++) {
            h.approved(2L, 1L, "CONTINUES");
        }

        KnowledgeThreadDTO thread = h.service().readingThread(1L);

        assertEquals(List.of(2L), postIds(thread.getDownstream()));
        assertEquals(51, h.maxRequestedThreadLimit,
                "the mapper must receive limit + 1 so query truncation is observable");
        assertTrue(thread.isTruncated());
    }

    @Test
    void hopBudgetTruncatesLongChainsHonestly() {
        Harness h = new Harness(publicPost(1L), publicPost(2L), publicPost(3L),
                publicPost(4L), publicPost(5L), publicPost(6L));
        for (long later = 2; later <= 6; later++) {
            h.approved(later, later - 1, "CONTINUES");
        }

        KnowledgeThreadDTO thread = h.service().readingThread(1L);

        assertEquals(List.of(2L, 3L, 4L), postIds(thread.getDownstream()),
                "walk must stop at MAX_THREAD_HOPS");
        assertTrue(thread.isTruncated(), "a cut chain must be reported as truncated");
    }

    private static List<Long> postIds(List<KnowledgeThreadNodeDTO> nodes) {
        return nodes.stream().map(KnowledgeThreadNodeDTO::getPostId).toList();
    }

    private static Post publicPost(Long id) {
        return Post.builder()
                .id(id)
                .title("Post " + id)
                .domain(3)
                .postStatus(Post.STATUS_PUBLISHED)
                .visibility(Post.VIS_PUBLIC)
                .contentEnvironment(Post.CONTENT_ENVIRONMENT_COMMUNITY)
                .build();
    }

    private static Post privatePost(Long id) {
        return Post.builder()
                .id(id)
                .title("Private " + id)
                .domain(3)
                .postStatus(Post.STATUS_PUBLISHED)
                .visibility(Post.VIS_SELF)
                .contentEnvironment(Post.CONTENT_ENVIRONMENT_COMMUNITY)
                .build();
    }

    private static final class Harness {
        private final Map<Long, Post> posts = new LinkedHashMap<>();
        private final List<PostKnowledgeRelationRow> rows = new ArrayList<>();
        private long nextRowId = 100;
        private int maxRequestedThreadLimit;

        private Harness(Post... seeded) {
            for (Post post : seeded) {
                posts.put(post.getId(), post);
            }
        }

        private void approved(long sourceId, long targetId, String type) {
            relation(sourceId, targetId, type, "APPROVED", "VISIBLE");
        }

        private void relation(long sourceId, long targetId, String type,
                              String reviewStatus, String visibilityStatus) {
            PostKnowledgeRelationRow row = new PostKnowledgeRelationRow();
            row.setId(nextRowId++);
            row.setSourcePostId(sourceId);
            row.setTargetPostId(targetId);
            row.setRelationType(type);
            row.setReviewStatus(reviewStatus);
            row.setVisibilityStatus(visibilityStatus);
            rows.add(row);
        }

        private PostKnowledgeRelationService service() {
            return new PostKnowledgeRelationService(
                    mapper(), repository(), new SnowflakeIdGenerator(), null, new ObjectMapper());
        }

        private PostKnowledgeRelationMapper mapper() {
            return (PostKnowledgeRelationMapper) Proxy.newProxyInstance(
                    Harness.class.getClassLoader(),
                    new Class<?>[] {PostKnowledgeRelationMapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "listPublicByPostId" -> {
                            Long postId = (Long) args[0];
                            int limit = (Integer) args[1];
                            yield rows.stream()
                                    .filter(row -> postId.equals(row.getSourcePostId())
                                            || postId.equals(row.getTargetPostId()))
                                    .filter(row -> "APPROVED".equals(row.getReviewStatus()))
                                    .filter(row -> "VISIBLE".equals(row.getVisibilityStatus()))
                                    .filter(row -> publiclyVisible(row.getSourcePostId())
                                            && publiclyVisible(row.getTargetPostId()))
                                    .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                                    .limit(limit)
                                    .toList();
                        }
                        // Mirrors the dedicated SQL: chain type and direction
                        // are filtered before ORDER BY / LIMIT.
                        case "listPublicChainByPostId" -> {
                            Long postId = (Long) args[0];
                            boolean upstream = (Boolean) args[1];
                            int limit = (Integer) args[2];
                            maxRequestedThreadLimit = Math.max(maxRequestedThreadLimit, limit);
                            yield rows.stream()
                                    .filter(row -> chainType(row.getRelationType()))
                                    .filter(row -> chainDirectionMatches(row, postId, upstream))
                                    .filter(row -> "APPROVED".equals(row.getReviewStatus()))
                                    .filter(row -> "VISIBLE".equals(row.getVisibilityStatus()))
                                    .filter(row -> publiclyVisible(row.getSourcePostId())
                                            && publiclyVisible(row.getTargetPostId()))
                                    .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                                    .limit(limit)
                                    .toList();
                        }
                        case "toString" -> "PostKnowledgeRelationMapperStub";
                        case "hashCode" -> 0;
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private static boolean chainType(String relationType) {
            return List.of("PREREQUISITE_OF", "CONTINUES", "SUPERSEDES")
                    .contains(relationType);
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

        private boolean publiclyVisible(Long postId) {
            Post post = posts.get(postId);
            return post != null && post.isVisibleTo(null, false);
        }

        private PostRepository repository() {
            return (PostRepository) Proxy.newProxyInstance(
                    Harness.class.getClassLoader(),
                    new Class<?>[] {PostRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> Optional.ofNullable(posts.get(args[0]));
                        case "toString" -> "PostRepositoryStub";
                        case "hashCode" -> 0;
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
