package com.offerlab.community.feed.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.feed.api.dto.ChannelHotBoardVO;
import com.offerlab.community.feed.api.dto.FeedItemVO;
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
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedDomainFilterTest {

    @Test
    void latestDomainFilterExcludesUnclassifiedPostsFromConcreteDomains() {
        PostBriefDTO unclassifiedPost = post(701L, 71L, null);
        PostBriefDTO careerPost = post(702L, 72L, Post.DOMAIN_CAREER);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(unclassifiedPost, careerPost), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> techPage = facade.getLatestFeed(null, null, 3, Post.DOMAIN_TECH);
        PageResult<FeedItemVO> careerPage = facade.getLatestFeed(null, null, 3, Post.DOMAIN_CAREER);

        assertEquals(List.of(), postIds(techPage));
        assertEquals(List.of(702L), postIds(careerPage));
        assertEquals("LATEST", careerPage.getItems().get(0).getSourceType());
        assertEquals("RECENT_PUBLISHED", careerPage.getItems().get(0).getReasonCode());
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
        facade.setKafkaEnabled(true);

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
        facade.setKafkaEnabled(true);

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
        facade.setKafkaEnabled(true);

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, null);

        assertEquals(List.of(922L), postIds(page));
    }

    @Test
    void feedSourcesExcludePostsFromBlockedAuthorsWithoutHidingOtherAuthors() {
        PostBriefDTO blocked = post(925L, 1_925L, Post.DOMAIN_TECH);
        PostBriefDTO visible = post(926L, 1_926L, Post.DOMAIN_TECH);
        FeedFacadeImpl latestFacade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of(), Set.of(), Set.of(1_925L)),
                new FakePostFacade(PageResult.of(List.of(blocked, visible), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> latest = latestFacade.getLatestFeed(7L, null, 2, null);
        PageResult<FeedItemVO> recommend = latestFacade.getRecommendFeed(7L, null, 2, null);

        assertEquals(List.of(926L), postIds(latest));
        assertEquals(List.of(926L), postIds(recommend));

        FeedFacadeImpl followingFacade = new FeedFacadeImpl(
                new ScriptedFeedInboxRedis(List.of(tuple("925", 2_000D), tuple("926", 1_999D))),
                new FixedHiddenFeedFeedbackStore(Set.of(), Set.of(), Set.of(1_925L)),
                new FakePostFacade(PageResult.of(List.of(blocked, visible), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });
        followingFacade.setKafkaEnabled(true);

        assertEquals(List.of(926L), postIds(followingFacade.getFollowingFeed(7L, null, 2, null)));
    }

    @Test
    void recommendFeedDemotesDomainsMarkedLessLikeThis() {
        PostBriefDTO tech = post(931L, 1931L, Post.DOMAIN_TECH);
        PostBriefDTO career = post(932L, 1932L, Post.DOMAIN_CAREER);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of(), Set.of(Post.DOMAIN_TECH)),
                new FakePostFacade(PageResult.of(List.of(tech, career), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> page = facade.getRecommendFeed(7L, null, 2, null);

        assertEquals(List.of(932L, 931L), postIds(page));
        assertEquals("RECOMMEND", page.getItems().get(0).getSourceType());
        assertEquals("RULE_MATCH", page.getItems().get(0).getReasonCode());
    }

    @Test
    void recommendFeedExposesNeutralStructuredReasonsWithoutRawIntentValues() {
        PostBriefDTO candidate = post(936L, 1_936L, Post.DOMAIN_CAREER);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(candidate), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> page = facade.getRecommendFeed(7L, null, 1, null);

        assertEquals("NEW_CREATOR_SUPPORT", page.getItems().get(0).getRecommendationReasonDetails().get(0).getCode());
        assertEquals("新作者前 3 篇内容扶持",
                page.getItems().get(0).getRecommendationReasonDetails().get(0).getText());
        assertTrue(page.getItems().get(0).getRecommendationReasons().stream()
                .noneMatch(reason -> reason.contains("1_936")));
    }

    @Test
    void crossDomainReasonNeverExposesRawViewerInterestValues() {
        String privateInterest = "private-interest-marker";
        PostBriefDTO candidate = post(9361L, 19_361L, Post.DOMAIN_CAREER);
        candidate.setTitle("包含 " + privateInterest + " 的公开讨论");
        UserIntentDTO intent = new UserIntentDTO();
        intent.setInterestTopics(List.of(privateInterest));
        FakeUserFacade userFacade = new FakeUserFacade() {
            @Override
            public UserIntentDTO getUserIntent(Long uid) {
                return intent;
            }
        };
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(candidate), null, false)),
                userFacade,
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        var page = facade.getCrossDomainRecommendations(7L, null, 1);

        assertEquals(1, page.getItems().size());
        assertFalse(page.getItems().get(0).getRecommendationReason().contains(privateInterest));
        assertFalse(page.getItems().get(0).getItem().getReasonText().contains(privateInterest));
    }

    @Test
    void channelHotBoardUsesExistingHotFeedAndRespectsViewerControls() {
        PostBriefDTO hidden = post(937L, 1_937L, Post.DOMAIN_TECH);
        PostBriefDTO visible = post(938L, 1_938L, Post.DOMAIN_TECH);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of(937L)),
                new FakePostFacade(PageResult.of(List.of(hidden, visible), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        ChannelHotBoardVO board = facade.getChannelHotBoard(7L, Post.DOMAIN_TECH, 10);

        assertEquals(Post.DOMAIN_TECH, board.getDomain());
        assertEquals("channel-hot.v1", board.getRuleVersion());
        assertEquals(1, board.getItems().size());
        assertEquals(1, board.getItems().get(0).getRank());
        assertEquals(938L, board.getItems().get(0).getItem().getPost().getId());
        assertEquals("按公开互动与发布时间综合排序", board.getItems().get(0).getReasonText());
    }

    @Test
    void recommendFeedHandlesTwentyMixedDomainCandidatesAndKeepsKnownDomainPenalty() {
        List<PostBriefDTO> candidates = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            Integer domain = switch (index) {
                case 0 -> null;
                case 1 -> 999;
                case 2 -> Post.DOMAIN_TECH;
                default -> Post.DOMAIN_CAREER;
            };
            candidates.add(post(1_000L + index, 2_000L + index, domain));
        }
        PageResult<PostBriefDTO> candidatePage = PageResult.of(candidates, null, false);
        FeedFacadeImpl noPreferenceFacade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of(), Set.of()),
                new FakePostFacade(candidatePage),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> noPreferencePage = assertDoesNotThrow(
                () -> noPreferenceFacade.getRecommendFeed(7L, null, 20, null));

        assertEquals(20, noPreferencePage.getItems().size());
        assertTrue(postIds(noPreferencePage).containsAll(List.of(1_000L, 1_001L)));

        FeedFacadeImpl reducedTechFacade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of(), Set.of(Post.DOMAIN_TECH)),
                new FakePostFacade(candidatePage),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });

        PageResult<FeedItemVO> reducedTechPage = assertDoesNotThrow(
                () -> reducedTechFacade.getRecommendFeed(7L, null, 20, null));

        assertEquals(20, reducedTechPage.getItems().size());
        assertEquals(1_002L, postIds(reducedTechPage).get(19));
    }

    @Test
    void followingFeedFallsBackToDatabaseWithoutCrossAccountState() {
        PostBriefDTO followed = post(941L, 1941L, Post.DOMAIN_TECH);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new FailingFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(followed), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });
        facade.setKafkaEnabled(true);

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, null);

        assertEquals(List.of(941L), postIds(page));
        assertEquals("following-db-fallback", page.getSource());
        assertTrue(page.getDegraded());
        assertEquals("FOLLOWING", page.getItems().get(0).getSourceType());
    }

    @Test
    void emptyFollowingInboxFallsBackToDatabaseForUnfannedPosts() {
        PostBriefDTO followed = post(951L, 1951L, Post.DOMAIN_CAREER);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(followed), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });
        facade.setKafkaEnabled(false);

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, null);

        assertEquals(List.of(951L), postIds(page));
        assertEquals("following-db-fallback", page.getSource());
        assertTrue(page.getDegraded());
    }

    @Test
    void kafkaBackedEmptyInboxDoesNotTriggerRepeatedDatabaseScans() {
        PostBriefDTO followed = post(961L, 1961L, Post.DOMAIN_CAREER);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(followed), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });
        facade.setKafkaEnabled(true);

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, null);

        assertEquals(List.of(), postIds(page));
    }

    @Test
    void disabledFeedConsumerUsesDatabaseFollowingFallback() {
        PostBriefDTO followed = post(971L, 1971L, Post.DOMAIN_TECH);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(followed), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });
        facade.setKafkaEnabled(true);
        facade.setFeedKafkaConsumerEnabled(false);

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, null);

        assertEquals(List.of(971L), postIds(page));
        assertEquals("following-db-fallback", page.getSource());
    }

    @Test
    void kafkaDisabledFeedKeepsDatabaseAsTheCorrectnessSourceWhenRedisHasData() {
        PostBriefDTO fanned = post(981L, 1981L, Post.DOMAIN_TECH);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new ScriptedFeedInboxRedis(List.of(tuple("981", 9810D))),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(PageResult.of(List.of(fanned), null, false)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });
        facade.setKafkaEnabled(false);

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 2, null);

        assertEquals(List.of(981L), postIds(page));
        assertEquals("FOLLOWING", page.getItems().get(0).getSourceType());
        assertEquals("following-db-fallback", page.getSource());
        assertTrue(page.getDegraded());
    }

    @Test
    void databaseFollowingQueryDoesNotLoseOlderFollowedPostsBehindGlobalTraffic() {
        List<PostBriefDTO> unrelatedLatest = new ArrayList<>();
        for (long id = 10_000L; id < 11_100L; id++) {
            unrelatedLatest.add(post(id, id + 50_000L, Post.DOMAIN_TECH));
        }
        PostBriefDTO olderFollowed = post(9_999L, 77L, Post.DOMAIN_TECH);
        FeedFacadeImpl facade = new FeedFacadeImpl(
                new EmptyFeedInboxRedis(),
                new FixedHiddenFeedFeedbackStore(Set.of()),
                new FakePostFacade(
                        PageResult.of(unrelatedLatest, null, false),
                        List.of(olderFollowed)),
                new FakeUserFacade(),
                new FakeInteractionFacade(),
                new ObjectMapper(),
                (viewerUid, domain, deliveredItemCount, supportHitItemCount) -> { });
        facade.setKafkaEnabled(false);

        PageResult<FeedItemVO> page = facade.getFollowingFeed(7L, null, 20, null);

        assertEquals(List.of(9_999L), postIds(page));
        assertEquals("following-db-fallback", page.getSource());
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

    private static class FailingFeedInboxRedis extends EmptyFeedInboxRedis {
        @Override
        public Set<ZSetOperations.TypedTuple<String>> readInboxWithScore(Long uid, double maxScoreExclusive, int size) {
            throw new IllegalStateException("redis unavailable");
        }
    }

    private static class FixedHiddenFeedFeedbackStore extends FeedFeedbackStore {
        private final Set<Long> hiddenPostIds;
        private final Set<Integer> lessLikedDomains;
        private final Set<Long> blockedAuthorIds;

        FixedHiddenFeedFeedbackStore(Set<Long> hiddenPostIds) {
            this(hiddenPostIds, Set.of(), Set.of());
        }

        FixedHiddenFeedFeedbackStore(Set<Long> hiddenPostIds, Set<Integer> lessLikedDomains) {
            this(hiddenPostIds, lessLikedDomains, Set.of());
        }

        FixedHiddenFeedFeedbackStore(Set<Long> hiddenPostIds,
                                     Set<Integer> lessLikedDomains,
                                     Set<Long> blockedAuthorIds) {
            super(null);
            this.hiddenPostIds = hiddenPostIds;
            this.lessLikedDomains = lessLikedDomains;
            this.blockedAuthorIds = blockedAuthorIds;
        }

        @Override
        public Set<Long> hiddenPostIds(Long uid) {
            return hiddenPostIds;
        }

        @Override
        public Set<Integer> lessLikedDomains(Long uid) {
            return lessLikedDomains;
        }

        @Override
        public Set<Long> blockedAuthorIds(Long uid) {
            return blockedAuthorIds;
        }
    }

    private static class FakePostFacade implements PostFacade {
        private final PageResult<PostBriefDTO> latestPage;
        private final List<PostBriefDTO> followingPosts;

        FakePostFacade(PageResult<PostBriefDTO> latestPage) {
            this(latestPage, latestPage.getItems());
        }

        FakePostFacade(PageResult<PostBriefDTO> latestPage, List<PostBriefDTO> followingPosts) {
            this.latestPage = latestPage;
            this.followingPosts = followingPosts;
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

        @Override
        public List<PostBriefDTO> listFollowingPostsByKeyset(Long viewerUid, Integer domain,
                                                             LocalDateTime cursorTime, Long cursorId, int size) {
            return followingPosts.stream()
                    .filter(post -> domain == null || java.util.Objects.equals(post.getDomain(), domain))
                    .filter(post -> cursorTime == null
                            || post.getCreateTime().isBefore(cursorTime)
                            || (post.getCreateTime().equals(cursorTime)
                            && cursorId != null
                            && post.getId() < cursorId))
                    .sorted(java.util.Comparator
                            .comparing(PostBriefDTO::getCreateTime).reversed()
                            .thenComparing(PostBriefDTO::getId, java.util.Comparator.reverseOrder()))
                    .limit(size)
                    .toList();
        }

        @Override public PostDTO getPost(Long postId) { throw unsupported(); }
        @Override public PostDTO getPostMetadata(Long postId) { return getPost(postId); }
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
        @Override public boolean updatePost(PostUpdateCmd cmd) { throw unsupported(); }
        @Override public void deletePost(Long postId, Long operatorUid) { throw unsupported(); }
        @Override public PageResult<PostBriefDTO> getPostsByAuthor(Long authorId, long cursor, int size) { throw unsupported(); }
        @Override
        public PageResult<PostBriefDTO> getHot(String cursor, int size) {
            return PageResult.of(latestPage.getItems().stream().limit(size).toList(), null, false);
        }
        @Override
        public PageResult<PostBriefDTO> getHot(String cursor, int size, Integer domain) {
            return PageResult.of(latestPage.getItems().stream()
                    .filter(post -> domain == null || java.util.Objects.equals(post.getDomain(), domain))
                    .limit(size)
                    .toList(), null, false);
        }
        @Override public List<PostVersionHistoryDTO> listPostVersions(Long postId, Long viewerUid, boolean moderator, int limit) { throw unsupported(); }
        @Override
        public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size) {
            List<PostBriefDTO> filtered = latestPage.getItems().stream()
                    .filter(post -> domain == null || java.util.Objects.equals(post.getDomain(), domain))
                    .limit(size)
                    .toList();
            return PageResult.of(filtered, null, false);
        }
        @Override public PageResult<PostBriefDTO> listPosts(Long authorId, Long tagId, Integer postType, Boolean featured, Integer domain, long cursor, int size, boolean includeTestData) { throw unsupported(); }
        @Override public List<TagDTO> listTags() { throw unsupported(); }
        @Override public TagDTO getTag(Long tagId) { throw unsupported(); }
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
        @Override public boolean isFollowing(Long fromUid, Long toUid) { return false; }
        @Override
        public Map<Long, Boolean> batchIsFollowing(Long fromUid, Collection<Long> toUids) {
            return toUids.stream().collect(java.util.stream.Collectors.toMap(uid -> uid, uid -> true));
        }
        @Override public List<Long> getFollowerIds(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<FollowCursorDTO> getFollowerPage(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<Long> getFollowingIds(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public List<FollowCursorDTO> getFollowingPage(Long uid, long cursor, int size) { throw unsupported(); }
        @Override public long getFollowerCount(Long uid) { throw unsupported(); }
        @Override public boolean isBigV(Long uid) { throw unsupported(); }
        @Override public UserIntentDTO getUserIntent(Long uid) { return null; }
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

    private static ZSetOperations.TypedTuple<String> tuple(String value, Double score) {
        return ZSetOperations.TypedTuple.of(value, score);
    }
}
