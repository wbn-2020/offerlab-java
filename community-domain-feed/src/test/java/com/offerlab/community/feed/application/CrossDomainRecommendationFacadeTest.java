package com.offerlab.community.feed.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.dto.CrossDomainRecommendationVO;
import com.offerlab.community.feed.api.quality.QualitySignalWindow;
import com.offerlab.community.feed.api.quality.RevisionAwareQualitySignalQueryResult;
import com.offerlab.community.feed.infrastructure.FeedFeedbackStore;
import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.*;
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
import com.offerlab.community.user.api.dto.ContactRequestPolicyCheckDTO;
import com.offerlab.community.user.api.dto.ContactRequestSettingsDTO;
import com.offerlab.community.user.api.dto.FollowCursorDTO;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossDomainRecommendationFacadeTest {

    @Test
    void crossDomainRecommendationsPreferExplainableForeignDomainCandidates() {
        PostBriefDTO lifestyle = post(5101L, 81L, Post.DOMAIN_LIFESTYLE, "上海通勤与租房避坑",
                "适合技术人落地新城市的生活经验",
                "{\"topic\":\"城市生活\",\"contentType\":\"图文笔记\"}",
                List.of(TagDTO.builder().name("城市生活").build()),
                5L, 1L, 1L, 1L,
                LocalDateTime.now().minusHours(6));
        PostBriefDTO career = post(5102L, 82L, Post.DOMAIN_CAREER, "转岗简历修改清单",
                "技术同学常见的简历表达问题",
                "{\"topic\":\"求职表达\"}",
                List.of(),
                1L, 0L, 0L, 0L,
                LocalDateTime.now().minusHours(12));
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(
                        Map.of(
                                Post.DOMAIN_CAREER, PageResult.of(List.of(career), null, false),
                                Post.DOMAIN_READING, PageResult.empty(),
                                Post.DOMAIN_LIFESTYLE, PageResult.of(List.of(lifestyle), null, false),
                                Post.DOMAIN_INVESTMENT, PageResult.empty()),
                        PageResult.empty(),
                        Map.of(81L, 2L, 82L, 6L)),
                new FakeUserFacade(
                        UserIntentDTO.builder()
                                .techStack(List.of("Redis"))
                                .interestTopics(List.of("城市生活"))
                                .interestTags(List.of("租房"))
                                .contentPreferences(List.of("图文笔记"))
                                .build(),
                        Map.of(
                                81L, UserBriefDTO.builder().uid(81L).nickname("lifestyle-author").build(),
                                82L, UserBriefDTO.builder().uid(82L).nickname("career-author").build())),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<CrossDomainRecommendationVO> page = facade.getCrossDomainRecommendations(7L, null, 2);

        assertFalse(Boolean.TRUE.equals(page.getDegraded()));
        assertEquals(2, page.getItems().size());
        assertEquals(5101L, page.getItems().get(0).getItem().getPost().getId());
        assertEquals(Post.DOMAIN_TECH, page.getItems().get(0).getSourceDomain());
        assertEquals(Post.DOMAIN_LIFESTYLE, page.getItems().get(0).getTargetDomain());
        assertFalse(page.getItems().get(0).getRecommendationReason().isBlank());
    }

    @Test
    void crossDomainRecommendationsFallbackToHotFeedWhenNoForeignDomainCandidates() {
        PostBriefDTO hotPost = post(5201L, 91L, Post.DOMAIN_TECH, "全站热门内容",
                "跨领域候选不足时的回退内容",
                null,
                List.of(),
                20L, 3L, 2L, 1L,
                LocalDateTime.now().minusHours(2));
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(
                        Map.of(
                                Post.DOMAIN_CAREER, PageResult.empty(),
                                Post.DOMAIN_READING, PageResult.empty(),
                                Post.DOMAIN_LIFESTYLE, PageResult.empty(),
                                Post.DOMAIN_INVESTMENT, PageResult.empty()),
                        PageResult.of(List.of(hotPost), null, false),
                        Map.of()),
                new FakeUserFacade(
                        null,
                        Map.of(91L, UserBriefDTO.builder().uid(91L).nickname("hot-author").build())),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<CrossDomainRecommendationVO> page = facade.getCrossDomainRecommendations(7L, null, 1);

        assertTrue(Boolean.TRUE.equals(page.getDegraded()));
        assertEquals("hot", page.getSource());
        assertEquals(1, page.getItems().size());
        assertTrue(page.getItems().get(0).getDegraded());
        assertEquals("跨领域候选不足，已回退到热门内容", page.getItems().get(0).getRecommendationReason());
        assertEquals(null, page.getItems().get(0).getSourceDomain());
        assertEquals(Post.DOMAIN_TECH, page.getItems().get(0).getTargetDomain());
    }

    @Test
    void crossDomainRecommendationsUseKeysetOrderSoSameBatchCandidatesAreNotSkipped() {
        LocalDateTime newer = LocalDateTime.of(2026, 8, 3, 12, 0);
        PostBriefDTO newerCareer = post(5301L, 101L, Post.DOMAIN_CAREER, "职场流程说明",
                "普通的职场内容",
                null,
                List.of(),
                0L, 0L, 0L, 0L,
                newer);
        PostBriefDTO olderLifestyle = post(5302L, 102L, Post.DOMAIN_LIFESTYLE, "上海通勤与租房避坑",
                "城市生活经验",
                "{\"topic\":\"城市生活\"}",
                List.of(TagDTO.builder().name("城市生活").build()),
                0L, 0L, 0L, 0L,
                newer.minusMinutes(1));
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(
                        Map.of(
                                Post.DOMAIN_CAREER, PageResult.of(List.of(newerCareer), null, false),
                                Post.DOMAIN_READING, PageResult.empty(),
                                Post.DOMAIN_LIFESTYLE, PageResult.of(List.of(olderLifestyle), null, false)),
                        PageResult.empty(),
                        Map.of(101L, 5L, 102L, 5L)),
                new FakeUserFacade(
                        UserIntentDTO.builder()
                                .techStack(List.of("Redis"))
                                .interestTopics(List.of("城市生活"))
                                .build(),
                        Map.of(
                                101L, UserBriefDTO.builder().uid(101L).nickname("career-author").build(),
                                102L, UserBriefDTO.builder().uid(102L).nickname("lifestyle-author").build())),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<CrossDomainRecommendationVO> firstPage = facade.getCrossDomainRecommendations(7L, null, 1);
        PageResult<CrossDomainRecommendationVO> secondPage = facade.getCrossDomainRecommendations(
                7L, firstPage.getNextCursor(), 1);

        assertEquals(5301L, firstPage.getItems().get(0).getItem().getPost().getId());
        assertTrue(Boolean.TRUE.equals(firstPage.getHasMore()));
        assertEquals(5302L, secondPage.getItems().get(0).getItem().getPost().getId());
        assertFalse(Boolean.TRUE.equals(secondPage.getHasMore()));
    }

    @Test
    void legacyFeedbackReasonCannotUseReservedV30NamespaceButReasonCodeCan() {
        PostBriefDTO post = post(5401L, 111L, Post.DOMAIN_TECH, "Redis 实践",
                "公开技术内容",
                null,
                List.of(),
                0L, 0L, 0L, 0L,
                LocalDateTime.of(2026, 8, 3, 10, 0));
        CapturingFeedFeedbackStore feedbackStore = new CapturingFeedFeedbackStore();
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                feedbackStore,
                new FakePostFacade(
                        Map.of(Post.DOMAIN_TECH, PageResult.of(List.of(post), null, false)),
                        PageResult.empty(),
                        Map.of(111L, 1L)),
                new FakeUserFacade(null,
                        Map.of(111L, UserBriefDTO.builder().uid(111L).nickname("tech-author").build())),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        assertThrows(BizException.class,
                () -> facade.recordFeedback(7L, 5401L, "HIDE", " V30:quality_not_expected ", null));
        assertEquals(0, feedbackStore.recordCount);

        facade.recordFeedback(7L, 5401L, "HIDE", "内容质量不符合预期", "quality_not_expected");

        assertEquals(1, feedbackStore.recordCount);
        assertEquals("v30:quality_not_expected", feedbackStore.recordedReason);
    }

    @Test
    void revisionAwareQualitySignalFacadeDelegatesTheOpaqueWindowContract() {
        CapturingFeedFeedbackStore feedbackStore = new CapturingFeedFeedbackStore();
        Instant baseWindowStart = Instant.parse("2026-08-01T00:00:00Z");
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        List<QualitySignalWindow> windows = List.of(
                new QualitySignalWindow(5501L, 115L, baseWindowStart.plusSeconds(1), true, "revision-2"));
        RevisionAwareQualitySignalQueryResult expected = RevisionAwareQualitySignalQueryResult.available(List.of(
                new RevisionAwareQualitySignalQueryResult.RevisionAwareQualitySignalSnapshot(
                        5501L, "revision-2", 3L, 4L)));
        feedbackStore.revisionAwareResult = expected;
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                feedbackStore,
                new FakePostFacade(Map.of(), PageResult.empty(), Map.of()),
                new FakeUserFacade(null, Map.of()),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        RevisionAwareQualitySignalQueryResult actual =
                facade.findRevisionAwareActiveQualitySignals(windows, baseWindowStart, now);

        assertEquals(expected, actual);
        assertEquals(windows, feedbackStore.revisionAwareWindows);
        assertEquals(baseWindowStart, feedbackStore.revisionAwareBaseWindowStart);
        assertEquals(now, feedbackStore.revisionAwareNow);
    }

    private static PostBriefDTO post(Long id,
                                     Long authorId,
                                     Integer domain,
                                     String title,
                                     String summary,
                                     String extJson,
                                     List<TagDTO> tags,
                                     Long viewCount,
                                     Long likeCount,
                                     Long commentCount,
                                     Long favoriteCount,
                                     LocalDateTime createTime) {
        return PostBriefDTO.builder()
                .id(id)
                .authorId(authorId)
                .domain(domain)
                .title(title)
                .summary(summary)
                .extJson(extJson)
                .tags(tags)
                .counter(PostCounterDTO.builder()
                        .postId(id)
                        .viewCount(viewCount)
                        .likeCount(likeCount)
                        .commentCount(commentCount)
                        .favoriteCount(favoriteCount)
                        .build())
                .createTime(createTime)
                .build();
    }

    private static class EmptyFeedInboxRedis extends FeedInboxRedis {
        EmptyFeedInboxRedis() {
            super(null, null);
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

    private static class CapturingFeedFeedbackStore extends FixedHiddenFeedFeedbackStore {
        private int recordCount;
        private String recordedReason;
        private List<QualitySignalWindow> revisionAwareWindows;
        private Instant revisionAwareBaseWindowStart;
        private Instant revisionAwareNow;
        private RevisionAwareQualitySignalQueryResult revisionAwareResult =
                RevisionAwareQualitySignalQueryResult.unavailable();

        CapturingFeedFeedbackStore() {
            super(Set.of());
        }

        @Override
        public void record(Long uid, Long postId, String action, String reason, Integer domain) {
            recordCount++;
            recordedReason = reason;
        }

        @Override
        public RevisionAwareQualitySignalQueryResult findRevisionAwareActiveQualitySignals(
                Collection<QualitySignalWindow> windows,
                Instant baseWindowStart,
                Instant now) {
            revisionAwareWindows = windows == null ? null : List.copyOf(windows);
            revisionAwareBaseWindowStart = baseWindowStart;
            revisionAwareNow = now;
            return revisionAwareResult;
        }
    }

    private static class FakePostFacade implements PostFacade {
        private final Map<Integer, PageResult<PostBriefDTO>> pagesByDomain;
        private final PageResult<PostBriefDTO> hotPage;
        private final Map<Long, Long> publicPostCountByAuthor;

        FakePostFacade(Map<Integer, PageResult<PostBriefDTO>> pagesByDomain,
                       PageResult<PostBriefDTO> hotPage,
                       Map<Long, Long> publicPostCountByAuthor) {
            this.pagesByDomain = pagesByDomain;
            this.hotPage = hotPage;
            this.publicPostCountByAuthor = publicPostCountByAuthor;
        }

        @Override
        public PageResult<PostBriefDTO> getHot(String cursor, int size) {
            return hotPage;
        }

        @Override
        public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size) {
            return pagesByDomain.getOrDefault(domain, PageResult.empty());
        }

        @Override
        public PageResult<PostBriefDTO> listPostsByKeyset(Long authorId, Long tagId, Integer postType,
                                                           Boolean featured, Integer domain,
                                                           LocalDateTime cursorTime, Long cursorId, int size) {
            List<PostBriefDTO> matches = pagesByDomain.getOrDefault(domain, PageResult.empty()).getItems().stream()
                    .filter(post -> isOlderThanKeyset(post, cursorTime, cursorId))
                    .sorted(java.util.Comparator.comparing(PostBriefDTO::getCreateTime).reversed()
                            .thenComparing(PostBriefDTO::getId, java.util.Comparator.reverseOrder()))
                    .toList();
            List<PostBriefDTO> items = matches.stream().limit(size).toList();
            return PageResult.of(items, null, matches.size() > items.size());
        }

        @Override
        public Map<Long, PostCounterDTO> batchGetCounters(Collection<Long> postIds) {
            return allPosts().stream()
                    .filter(post -> postIds.contains(post.getId()))
                    .collect(java.util.stream.Collectors.toMap(
                            PostBriefDTO::getId,
                            PostBriefDTO::getCounter));
        }

        @Override
        public Map<Long, Long> batchCountPublicPublishedPostsByAuthors(Collection<Long> authorIds) {
            Map<Long, Long> counts = new HashMap<>();
            for (Long authorId : authorIds) {
                counts.put(authorId, publicPostCountByAuthor.getOrDefault(authorId, 0L));
            }
            return counts;
        }

        private List<PostBriefDTO> allPosts() {
            return java.util.stream.Stream.concat(
                            pagesByDomain.values().stream()
                                    .filter(Objects::nonNull)
                                    .flatMap(page -> page.getItems().stream()),
                            hotPage == null ? java.util.stream.Stream.empty() : hotPage.getItems().stream())
                    .toList();
        }

        private static boolean isOlderThanKeyset(PostBriefDTO post, LocalDateTime cursorTime, Long cursorId) {
            if (cursorTime == null) {
                return true;
            }
            int timeOrder = post.getCreateTime().compareTo(cursorTime);
            return timeOrder < 0 || (timeOrder == 0 && post.getId() < cursorId);
        }

        @Override public PostDTO getPost(Long postId) { throw unsupported(); }
        @Override public PostDTO getPostMetadata(Long postId) { return getPost(postId); }
        @Override public PostDTO getPost(Long postId, Long viewerUid) { throw unsupported(); }
        @Override public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds) { throw unsupported(); }
        @Override
        public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid) {
            return allPosts().stream()
                    .filter(post -> postIds.contains(post.getId()))
                    .collect(java.util.stream.Collectors.toMap(PostBriefDTO::getId, post -> post));
        }
        @Override public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid, boolean includeTestData) { throw unsupported(); }
        @Override public Long publishPost(PostCreateCmd cmd) { throw unsupported(); }
        @Override public boolean updatePost(PostUpdateCmd cmd) { throw unsupported(); }
        @Override public void deletePost(Long postId, Long operatorUid) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> getPostsByAuthor(Long authorId, long cursor, int size) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> getLatest(long cursor, int size) { throw unsupported(); }
        @Override public List<PostVersionHistoryDTO> listPostVersions(Long postId, Long viewerUid, boolean moderator, int limit) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size, boolean includeTestData) { throw unsupported(); }
        @Override public List<TagDTO> listTags() { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> getPostsByTag(Long tagId, Integer postType, Boolean featured, long cursor, int size) { throw unsupported(); }
    }

    private static class FakeUserFacade implements UserFacade {
        private final UserIntentDTO intent;
        private final Map<Long, UserBriefDTO> authors;

        FakeUserFacade(UserIntentDTO intent, Map<Long, UserBriefDTO> authors) {
            this.intent = intent;
            this.authors = authors;
        }

        @Override
        public Map<Long, UserBriefDTO> batchGetUserBriefs(Collection<Long> uids) {
            return authors.entrySet().stream()
                    .filter(entry -> uids.contains(entry.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        @Override
        public UserIntentDTO getUserIntent(Long uid) {
            return intent;
        }

        @Override public UserBriefDTO getUserBrief(Long uid) { throw unsupported(); }
        @Override public Map<String, Long> findUserIdsByNicknames(Collection<String> nicknames) { throw unsupported(); }
        @Override public boolean isFollowing(Long fromUid, Long toUid) { return false; }
        @Override public Map<Long, Boolean> batchIsFollowing(Long fromUid, Collection<Long> toUids) { throw unsupported(); }
        @Override public List<Long> getFollowerIds(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<FollowCursorDTO> getFollowerPage(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<Long> getFollowingIds(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<FollowCursorDTO> getFollowingPage(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public long getFollowerCount(Long uid) { throw unsupported(); }
        @Override public boolean isBigV(Long uid) { throw unsupported(); }
        @Override public boolean isProfileVisible(Long viewerUid, Long targetUid) { return !Long.valueOf(0L).equals(targetUid); }
        @Override public boolean isIntentVisible(Long viewerUid, Long targetUid) { return !Long.valueOf(0L).equals(targetUid); }
        @Override public boolean isSearchable(Long uid) { throw unsupported(); }
        @Override public boolean allowsInteractionNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsSystemNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsLikeNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsCommentNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsFollowNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsFavoriteNotification(Long uid) { throw unsupported(); }
        @Override public boolean allowsMentionNotification(Long uid) { throw unsupported(); }
        @Override public ContactRequestSettingsDTO getContactRequestSettings(Long uid) { throw unsupported(); }
        @Override public ContactRequestPolicyCheckDTO checkContactRequestPolicy(Long requesterUid, Long receiverUid) { throw unsupported(); }
    }

    private static class FakeInteractionFacade implements InteractionFacade {
        @Override public void like(Long uid, Long postId) { throw unsupported(); }
        @Override public void unlike(Long uid, Long postId) { throw unsupported(); }
        @Override public boolean hasLiked(Long uid, Long postId) { return false; }
        @Override public boolean hasFavorited(Long uid, Long postId) { return false; }
        @Override public Set<Long> likedPostIds(Long uid, List<Long> postIds) { return Set.of(); }
        @Override public Set<Long> favoritedPostIds(Long uid, List<Long> postIds) { return Set.of(); }
        @Override public void likeComment(Long uid, Long commentId) { throw unsupported(); }
        @Override public void unlikeComment(Long uid, Long commentId) { throw unsupported(); }
        @Override public void favorite(Long uid, Long postId) { throw unsupported(); }
        @Override public void favorite(Long uid, Long postId, Long folderId) { throw unsupported(); }
        @Override public void unfavorite(Long uid, Long postId) { throw unsupported(); }
        @Override public Long addComment(CommentCreateCmd cmd) { throw unsupported(); }
        @Override public PageResult<CommentDTO> listComments(Long postId, Long viewerUid, long cursor, int size) { throw unsupported(); }
        @Override public PageResult<CommentDTO> listComments(Long postId, Long viewerUid, String cursor, int size, String sort) { throw unsupported(); }
        @Override public CommentDTO getCommentContext(Long postId, Long commentId, Long viewerUid) { throw unsupported(); }
        @Override public PageResult<CommentDTO> listCommentReplies(Long postId, Long rootId, Long viewerUid, String cursor, int size) { throw unsupported(); }
        @Override public void deleteComment(Long commentId, Long operatorUid) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listLikedPosts(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listLikedPosts(Long uid, String cursor, int size) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listFavoritePosts(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listFavoritePosts(Long uid, String cursor, int size) { throw unsupported(); }
        @Override public List<FavoriteFolderDTO> listFavoriteFolders(Long uid) { throw unsupported(); }
        @Override public FavoriteFolderDTO createFavoriteFolder(Long uid, FavoriteFolderCreateCmd cmd) { throw unsupported(); }
        @Override public FavoriteFolderDTO updateFavoriteFolder(Long uid, Long folderId, FavoriteFolderUpdateCmd cmd) { throw unsupported(); }
        @Override public FavoriteFolderDTO sortFavoriteFolder(Long uid, Long folderId, FavoriteFolderSortCmd cmd) { throw unsupported(); }
        @Override public List<FavoriteFolderDTO> reorderFavoriteFolders(Long uid, FavoriteFolderReorderCmd cmd) { throw unsupported(); }
        @Override public void deleteFavoriteFolder(Long uid, Long folderId, Long targetFolderId) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listFavoritePostsInFolder(Long uid, Long folderId, String cursor, int size) { throw unsupported(); }
        @Override public FavoriteFolderDTO moveFavorite(Long uid, Long postId, FavoriteMoveCmd cmd) { throw unsupported(); }
        @Override public FavoriteFolderDTO batchMoveFavorites(Long uid, FavoriteBatchMoveCmd cmd) { throw unsupported(); }
        @Override public FavoriteFolderDTO getPublicFavoriteFolder(Long folderId) { throw unsupported(); }
        @Override public List<FavoriteFolderDTO> listPublicFavoriteFoldersByUser(Long uid, int limit) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> listPublicFavoritePostsInFolder(Long folderId, String cursor, int size) { throw unsupported(); }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not needed for this test");
    }
}
