package com.offerlab.community.feed.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.dto.FeedItemVO;
import com.offerlab.community.feed.infrastructure.FeedFeedbackStore;
import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostCounterDTO;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.api.dto.PostVersionHistoryDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.FollowCursorDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedDomainFilterTest {

    @Test
    void latestDomainFilterTreatsMissingDomainAsTech() {
        PostBriefDTO legacyTechPost = post(701L, 71L, null);
        PostBriefDTO careerPost = post(702L, 72L, Post.DOMAIN_CAREER);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(legacyTechPost, careerPost), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> techPage = facade.getLatestFeed(null, null, 3, Post.DOMAIN_TECH);
        PageResult<FeedItemVO> careerPage = facade.getLatestFeed(null, null, 3, Post.DOMAIN_CAREER);

        assertEquals(List.of(701L), postIds(techPage));
        assertEquals(List.of(702L), postIds(careerPage));
    }

    @Test
    void feedItemAuthorUsesAlreadyMaskedPostAuthorForAnonymousPosts() {
        UserBriefDTO anonymousAuthor = UserBriefDTO.builder()
                .uid(0L)
                .nickname("匿名用户")
                .profileVisible(false)
                .build();
        PostBriefDTO anonymousPost = post(711L, 0L, Post.DOMAIN_CAREER);
        anonymousPost.setAnonymous(true);
        anonymousPost.setAuthor(anonymousAuthor);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(anonymousPost), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> page = facade.getLatestFeed(7L, null, 1, Post.DOMAIN_CAREER);

        assertEquals("匿名用户", page.getItems().get(0).getAuthor().getNickname());
        assertEquals(false, page.getItems().get(0).getAuthor().getProfileVisible());
    }

    @Test
    void followingDomainFilterScansSparseInboxUntilDomainPageFilled() {
        List<PostBriefDTO> posts = new ArrayList<>();
        List<ZSetOperations.TypedTuple<String>> tuples = new ArrayList<>();
        double score = 2_000D;
        for (long id = 801L; id <= 810L; id++) {
            posts.add(post(id, id + 1_000L, Post.DOMAIN_TECH));
            tuples.add(tuple(String.valueOf(id), score--));
        }
        posts.add(post(901L, 1901L, Post.DOMAIN_CAREER));
        posts.add(post(902L, 1902L, Post.DOMAIN_CAREER));
        tuples.add(tuple("901", score--));
        tuples.add(tuple("902", score));

        FeedFacadeImpl facade = new FeedFacadeImpl(
                new ScriptedFeedInboxRedis(tuples),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(posts, null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, Post.DOMAIN_CAREER);

        assertEquals(List.of(901L, 902L), postIds(page));
        assertEquals(Boolean.FALSE, page.getHasMore());
        assertEquals(null, page.getNextCursor());
    }

    @Test
    void followingDomainFilterDoesNotReturnEmptyPageWithHasMoreWhenScanLimitIsExhausted() {
        List<PostBriefDTO> posts = new ArrayList<>();
        List<ZSetOperations.TypedTuple<String>> tuples = new ArrayList<>();
        double score = 5_000D;
        for (long id = 1_001L; id <= 2_100L; id++) {
            posts.add(post(id, id + 10_000L, Post.DOMAIN_TECH));
            tuples.add(tuple(String.valueOf(id), score--));
        }

        FeedFacadeImpl facade = new FeedFacadeImpl(
                new ScriptedFeedInboxRedis(tuples),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(posts, null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, Post.DOMAIN_CAREER);

        assertEquals(List.of(), postIds(page));
        assertEquals(Boolean.FALSE, page.getHasMore());
        assertEquals(null, page.getNextCursor());
        assertEquals(1000, page.getDiagnostics().get("domainInboxScanRows"));
        assertEquals(true, page.getDiagnostics().get("domainInboxScanLimited"));
    }

    @Test
    void latestFeedFiltersPostsHiddenByFeedFeedback() {
        PostBriefDTO hidden = post(911L, 1911L, Post.DOMAIN_TECH);
        PostBriefDTO visible = post(912L, 1912L, Post.DOMAIN_TECH);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of(911L)),
                new FakePostFacade(PageResult.of(List.of(hidden, visible), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> page = facade.getLatestFeed(7L, null, 2, null);

        assertEquals(List.of(912L), postIds(page));
    }

    @Test
    void followingFeedFiltersPostsHiddenByFeedFeedback() {
        List<PostBriefDTO> posts = List.of(
                post(921L, 1921L, Post.DOMAIN_TECH),
                post(922L, 1922L, Post.DOMAIN_TECH));
        List<ZSetOperations.TypedTuple<String>> tuples = List.of(
                tuple("921", 2_000D),
                tuple("922", 1_999D));
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new ScriptedFeedInboxRedis(tuples),
                new FixedHiddenFeedFeedbackStore(Set.of(921L)),
                new FakePostFacade(PageResult.of(posts, null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, null);

        assertEquals(List.of(922L), postIds(page));
    }

    private static PostBriefDTO post(Long id, Long authorId, Integer domain) {
        return PostBriefDTO.builder()
                .id(id)
                .authorId(authorId)
                .title("post " + id)
                .summary("summary")
                .domain(domain)
                .createTime(LocalDateTime.of(2026, 1, 3, 0, 0).minusHours(id % 10))
                .build();
    }

    private static List<Long> postIds(PageResult<FeedItemVO> page) {
        return page.getItems().stream()
                .map(FeedItemVO::getPost)
                .map(PostBriefDTO::getId)
                .toList();
    }

    private static class EmptyFeedInboxRedis extends FeedInboxRedis {
        EmptyFeedInboxRedis() {
            super(null, null);
        }

        @Override
        public Set<ZSetOperations.TypedTuple<String>> readInboxWithScore(Long uid, double maxScoreExclusive, int size) {
            return Set.of();
        }

        @Override
        public Set<ZSetOperations.TypedTuple<String>> readGlobalLatest(double maxScoreExclusive, int size) {
            return Set.of();
        }
    }

    private static class ScriptedFeedInboxRedis extends EmptyFeedInboxRedis {
        private final List<ZSetOperations.TypedTuple<String>> tuples;

        ScriptedFeedInboxRedis(List<ZSetOperations.TypedTuple<String>> tuples) {
            this.tuples = tuples;
        }

        @Override
        public Set<ZSetOperations.TypedTuple<String>> readInboxWithScore(Long uid, double maxScoreExclusive, int size) {
            LinkedHashSet<ZSetOperations.TypedTuple<String>> page = new LinkedHashSet<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getScore() != null && tuple.getScore() <= maxScoreExclusive) {
                    page.add(tuple);
                }
                if (page.size() >= size) {
                    break;
                }
            }
            return page;
        }
    }

    private static class FixedHiddenFeedFeedbackStore extends FeedFeedbackStore {
        private final Set<Long> hiddenPostIds;

        FixedHiddenFeedFeedbackStore(Set<Long> hiddenPostIds) {
            super(null);
            this.hiddenPostIds = hiddenPostIds;
        }

        @Override
        public Set<Long> hiddenPostIds(Long uid) {
            return hiddenPostIds;
        }
    }

    private static class FakePostFacade implements PostFacade {
        private final PageResult<PostBriefDTO> latestPage;

        FakePostFacade(PageResult<PostBriefDTO> latestPage) {
            this.latestPage = latestPage;
        }

        @Override
        public PageResult<PostBriefDTO> getLatest(long cursor, int size) {
            return latestPage;
        }

        @Override
        public Map<Long, PostCounterDTO> batchGetCounters(Collection<Long> postIds) {
            return postIds.stream().collect(java.util.stream.Collectors.toMap(
                    id -> id,
                    id -> PostCounterDTO.builder()
                            .postId(id)
                            .viewCount(0L)
                            .likeCount(0L)
                            .commentCount(0L)
                            .favoriteCount(0L)
                            .build()));
        }

        @Override
        public Map<Long, Long> batchCountPublicPublishedPostsByAuthors(Collection<Long> authorIds) {
            return latestPage.getItems().stream()
                    .filter(post -> post.getAuthorId() != null && authorIds.contains(post.getAuthorId()))
                    .collect(java.util.stream.Collectors.groupingBy(
                            PostBriefDTO::getAuthorId,
                            java.util.stream.Collectors.counting()));
        }

        @Override public PostDTO getPost(Long postId) { throw unsupported(); }
        @Override public PostDTO getPost(Long postId, Long viewerUid) { throw unsupported(); }
        @Override public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds) { return batchGetPosts(postIds, null); }
        @Override public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid) { return batchGetPosts(postIds, viewerUid, false); }
        @Override
        public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid, boolean includeTestData) {
            return latestPage.getItems().stream()
                    .filter(post -> postIds.contains(post.getId()))
                    .collect(java.util.stream.Collectors.toMap(PostBriefDTO::getId, post -> post));
        }
        @Override public Long publishPost(PostCreateCmd cmd) { throw unsupported(); }
        @Override public void updatePost(PostUpdateCmd cmd) { throw unsupported(); }
        @Override public void deletePost(Long postId, Long operatorUid) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> getPostsByAuthor(Long authorId, long cursor, int size) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> getHot(String cursor, int size) { throw unsupported(); }
        @Override public List<PostVersionHistoryDTO> listPostVersions(Long postId, Long viewerUid, boolean moderator, int limit) { throw unsupported(); }
        @Override
        public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size) {
            List<PostBriefDTO> filtered = latestPage.getItems().stream()
                    .filter(post -> domain == null || (post.getDomain() == null ? Post.DOMAIN_TECH : post.getDomain()) == domain)
                    .limit(size)
                    .toList();
            return PageResult.of(filtered, null, false);
        }
        @Override public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size, boolean includeTestData) { throw unsupported(); }
        @Override public List<TagDTO> listTags() { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> getPostsByTag(Long tagId, Integer postType, Boolean featured, long cursor, int size) { throw unsupported(); }
    }

    private static class FakeUserFacade implements UserFacade {
        @Override
        public Map<Long, UserBriefDTO> batchGetUserBriefs(Collection<Long> uids) {
            return uids.stream().collect(java.util.stream.Collectors.toMap(
                    uid -> uid,
                    uid -> UserBriefDTO.builder().uid(uid).nickname("user " + uid).build()));
        }

        @Override public UserBriefDTO getUserBrief(Long uid) { throw unsupported(); }
        @Override public Map<String, Long> findUserIdsByNicknames(Collection<String> nicknames) { throw unsupported(); }
        @Override public boolean isFollowing(Long fromUid, Long toUid) { throw unsupported(); }
        @Override public Map<Long, Boolean> batchIsFollowing(Long fromUid, Collection<Long> toUids) { throw unsupported(); }
        @Override public List<Long> getFollowerIds(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<FollowCursorDTO> getFollowerPage(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<Long> getFollowingIds(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<FollowCursorDTO> getFollowingPage(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public long getFollowerCount(Long uid) { throw unsupported(); }
        @Override public boolean isBigV(Long uid) { throw unsupported(); }
        @Override public UserIntentDTO getUserIntent(Long uid) { throw unsupported(); }
        @Override public boolean isProfileVisible(Long viewerUid, Long targetUid) { throw unsupported(); }
        @Override public boolean isIntentVisible(Long viewerUid, Long targetUid) { throw unsupported(); }
        @Override public boolean isSearchable(Long uid) { throw unsupported(); }
        @Override public boolean allowsInteractionNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsSystemNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsLikeNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsCommentNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsFollowNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsFavoriteNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsMentionNotification(Long uid) { throw unsupported(); }
    }

    private static class FakeInteractionFacade implements InteractionFacade {
        @Override public void like(Long uid, Long postId) { throw unsupported(); }
        @Override public void unlike(Long uid, Long postId) { throw unsupported(); }
        @Override public boolean hasLiked(Long uid, Long postId) { return false; }
        @Override public boolean hasFavorited(Long uid, Long postId) { return false; }
        @Override public void likeComment(Long uid, Long commentId) { throw unsupported(); }
        @Override public void unlikeComment(Long uid, Long commentId) { throw unsupported(); }
        @Override public void favorite(Long uid, Long postId) { throw unsupported(); }
        @Override public void unfavorite(Long uid, Long postId) { throw unsupported(); }
        @Override public Long addComment(CommentCreateCmd cmd) { throw unsupported(); }
        @Override public PageResult<CommentDTO> listComments(Long postId, Long viewerUid, long cursor, int size) { throw unsupported(); }
        @Override public void deleteComment(Long commentId, Long operatorUid) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listLikedPosts(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listFavoritePosts(Long uid, long cursor, int size) { throw unsupported(); }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not needed for this test");
    }

    private static ZSetOperations.TypedTuple<String> tuple(String value, Double score) {
        return ZSetOperations.TypedTuple.of(value, score);
    }
}
