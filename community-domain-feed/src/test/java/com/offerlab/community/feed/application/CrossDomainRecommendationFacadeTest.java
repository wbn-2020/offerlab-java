package com.offerlab.community.feed.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.dto.CrossDomainRecommendationVO;
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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(page.getItems().get(0).getRecommendationReason().contains("城市生活"));
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
                        UserIntentDTO.builder().techStack(List.of("Java")).build(),
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
        assertEquals(Post.DOMAIN_TECH, page.getItems().get(0).getTargetDomain());
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

        @Override public PostDTO getPost(Long postId) { throw unsupported(); }
        @Override public PostDTO getPost(Long postId, Long viewerUid) { throw unsupported(); }
        @Override public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds) { throw unsupported(); }
        @Override public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid) { throw unsupported(); }
        @Override public Map<Long, PostBriefDTO> batchGetPosts(Collection<Long> postIds, Long viewerUid, boolean includeTestData) { throw unsupported(); }
        @Override public Long publishPost(PostCreateCmd cmd) { throw unsupported(); }
        @Override public void updatePost(PostUpdateCmd cmd) { throw unsupported(); }
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
