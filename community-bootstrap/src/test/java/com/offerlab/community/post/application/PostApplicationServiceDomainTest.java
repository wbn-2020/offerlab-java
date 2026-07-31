package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.review.ReviewQueueItemCommand;
import com.offerlab.community.infra.review.ReviewQueueReopenRequestedEvent;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostTagRefMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.TagPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostApplicationServiceDomainTest {
    @Mock private PostRepository postRepo;
    @Mock private PostCounterMapper counterMapper;
    @Mock private PostTagRefMapper postTagRefMapper;
    @Mock private TagMapper tagMapper;
    @Mock private PostCounterRedis postCounterRedis;
    @Mock private SnowflakeIdGenerator idGen;
    @Mock private EventPublisher events;
    @Mock private PostVersionHistoryService versionHistoryService;
    @Mock private PostPublishQualityValidator qualityValidator;
    @Mock private AfterCommitExecutor afterCommit;
    @Mock private CommunityTopicNotificationTargetService communityTopicNotificationTargetService;
    @Mock private MigrationCheckService migrationCheckService;
    @Mock private DomainConfigService domainConfigService;
    @Mock private ContentModerationService contentModerationService;
    @Mock private MultiLevelCache<PostDTO> postDetailCache;
    @Mock private DomainModeratorService domainModeratorService;
    @Mock private ApplicationEventPublisher springEvents;

    @InjectMocks
    private PostApplicationService service;

    @Test
    void publishRequiresExplicitDomainAndIgnoresLegacyExtJsonDomain() {
        PostCreateCmd cmd = PostCreateCmd.builder()
                .authorId(7L)
                .postType(Post.TYPE_TECH_ARTICLE)
                .title("explicit domain required")
                .content("legacy extJson must not select the publishing channel")
                .extJson("{\"domain\":2}")
                .build();

        BizException ex = assertThrows(BizException.class, () -> service.publish(cmd));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertEquals("请选择频道", ex.getMessage());
        assertEquals(Map.of("fieldErrors", Map.of("domain", "请选择频道")), ex.getData());
        verifyNoInteractions(domainConfigService, qualityValidator, postRepo);
    }

    @Test
    void updateWithoutExplicitDomainKeepsOriginalPostDomainDespiteExtJson() {
        Post post = Post.builder()
                .id(81L)
                .authorId(7L)
                .postType(Post.TYPE_TECH_ARTICLE)
                .title("existing title")
                .content("existing content long enough for the mocked validator")
                .visibility(Post.VIS_PUBLIC)
                .postStatus(Post.STATUS_PUBLISHED)
                .domain(Post.DOMAIN_TECH)
                .version(3)
                .build();
        when(postRepo.findById(81L)).thenReturn(Optional.of(post));
        when(tagMapper.selectTagsByPostIdsCompat(List.of(81L))).thenReturn(List.of());
        when(qualityValidator.validate(
                Post.TYPE_TECH_ARTICLE,
                "existing title",
                "existing content long enough for the mocked validator",
                "{\"domain\":2}",
                List.of(),
                List.of()))
                .thenReturn(new PostPublishQualityValidator.ValidatedPostInput(
                        Post.TYPE_TECH_ARTICLE,
                        "existing title",
                        "existing content long enough for the mocked validator",
                        "{\"domain\":2}",
                        List.of(),
                        List.of()));
        when(postRepo.update(any(Post.class))).thenReturn(true);

        service.update(PostUpdateCmd.builder()
                .postId(81L)
                .operatorUid(7L)
                .extJson("{\"domain\":2}")
                .build());

        assertEquals(Post.DOMAIN_TECH, post.getDomain());
        assertTrue(post.getExtJson().contains("\"domain\":1"));
        verify(domainConfigService).requireDomainEnabled(Post.DOMAIN_TECH);
    }

    @Test
    void updateRequiresPublicSummaryWhenRespondingToSuggestion() {
        arrangeValidUpdate(82L);

        BizException ex = assertThrows(BizException.class, () -> service.update(PostUpdateCmd.builder()
                .postId(82L)
                .operatorUid(7L)
                .respondedSuggestionIds(List.of(9001L))
                .build()));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(String.valueOf(ex.getData()).contains("publicUpdateSummary"));
        verifyNoInteractions(qualityValidator, versionHistoryService);
        org.mockito.Mockito.verify(postRepo, org.mockito.Mockito.never()).update(any(Post.class));
    }

    @Test
    void updateRequiresPublicSummaryWhenDeclaringImpactScope() {
        arrangeValidUpdate(83L);

        BizException ex = assertThrows(BizException.class, () -> service.update(PostUpdateCmd.builder()
                .postId(83L)
                .operatorUid(7L)
                .impactScope("公开内容与示例段落")
                .build()));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        assertTrue(String.valueOf(ex.getData()).contains("publicUpdateSummary"));
        verifyNoInteractions(qualityValidator, versionHistoryService);
        org.mockito.Mockito.verify(postRepo, org.mockito.Mockito.never()).update(any(Post.class));
    }

    @Test
    void moderationSourceAuthorizationRejectsNonOwner() {
        Post post = Post.builder()
                .id(84L)
                .authorId(7L)
                .build();
        when(postRepo.findById(84L)).thenReturn(Optional.of(post));

        BizException ex = assertThrows(BizException.class, () -> service.requireAuthorized(8L, 84L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verifyNoInteractions(contentModerationService, springEvents);
    }

    @Test
    void moderationSourceAuthorizationRejectsMissingPost() {
        when(postRepo.findById(85L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.requireAuthorized(7L, 85L));

        assertEquals(ErrorCode.POST_NOT_FOUND.getCode(), ex.getCode());
        verifyNoInteractions(contentModerationService, springEvents);
    }

    @Test
    void deleteEvictsPostCounterAfterTransactionCommit() {
        Post post = Post.builder()
                .id(91L)
                .authorId(7L)
                .postStatus(Post.STATUS_PUBLISHED)
                .build();
        when(postRepo.findById(91L)).thenReturn(Optional.of(post));

        service.delete(91L, 7L);

        var task = new AtomicReference<Runnable>();
        var description = new AtomicReference<String>();
        org.mockito.ArgumentCaptor<Runnable> taskCaptor = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        org.mockito.ArgumentCaptor<String> descriptionCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(afterCommit).execute(taskCaptor.capture(), descriptionCaptor.capture());
        task.set(taskCaptor.getValue());
        description.set(descriptionCaptor.getValue());
        task.get().run();

        verify(postCounterRedis).evict(91L);
        assertTrue(description.get().contains("post counter eviction"));
    }

    @Test
    void publishPolicyReviewReopensQueueWithoutPublishing() {
        when(qualityValidator.validate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PostPublishQualityValidator.ValidatedPostInput(
                        Post.TYPE_TECH_ARTICLE,
                        "investment review",
                        "content",
                        null,
                        List.of(11L),
                        List.of()));
        when(domainConfigService.reviewRequiredForPublish(Post.DOMAIN_INVESTMENT)).thenReturn(true);
        when(domainConfigService.riskLevelForDomain(Post.DOMAIN_INVESTMENT)).thenReturn("high");
        when(tagMapper.selectTagsByPostIdsCompat(List.of(101L))).thenReturn(List.of());
        when(tagMapper.selectByIdsCompat(List.of(11L))).thenReturn(List.of(tag(11L)));

        service.publish(PostCreateCmd.builder()
                .postId(101L)
                .authorId(7L)
                .postType(Post.TYPE_TECH_ARTICLE)
                .domain(Post.DOMAIN_INVESTMENT)
                .title("investment review")
                .content("content")
                .tagIds(List.of(11L))
                .build());

        var event = org.mockito.ArgumentCaptor.forClass(ReviewQueueReopenRequestedEvent.class);
        verify(springEvents).publishEvent(event.capture());
        ReviewQueueItemCommand command = event.getValue().command();
        assertEquals(PostApplicationService.PENDING_REVIEW_SOURCE_TYPE, command.sourceType());
        assertEquals(101L, command.sourceId());
        assertTrue(command.summary().contains("内容：content"));
        org.mockito.Mockito.verify(events, org.mockito.Mockito.never()).publish(any());
    }

    @Test
    void resolvePendingReviewPublishesAndEvictsAfterCommit() {
        Post post = Post.builder()
                .id(102L)
                .authorId(7L)
                .postType(Post.TYPE_TECH_ARTICLE)
                .title("reviewed")
                .content("content")
                .visibility(Post.VIS_PUBLIC)
                .postStatus(Post.STATUS_REVIEWING)
                .domain(Post.DOMAIN_INVESTMENT)
                .version(3)
                .build();
        when(postRepo.findById(102L)).thenReturn(Optional.of(post));
        when(postRepo.updateStatusIfCurrent(102L, Post.STATUS_REVIEWING, Post.STATUS_PUBLISHED, 3)).thenReturn(true);
        when(tagMapper.selectTagsByPostIdsCompat(List.of(102L))).thenReturn(List.of());

        service.resolvePendingPostReview(102L, 99L, true, "approved");

        verify(events).publish(any());
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).execute(task.capture(), org.mockito.ArgumentMatchers.contains("post review detail eviction"));
        verifyNoInteractions(postDetailCache);
        task.getValue().run();
        org.mockito.Mockito.verify(postDetailCache, org.mockito.Mockito.times(2)).evict(any());
    }

    @Test
    void resolvePendingReviewRejectsAuthorSelfReview() {
        Post post = Post.builder()
                .id(103L)
                .authorId(7L)
                .postStatus(Post.STATUS_REVIEWING)
                .domain(Post.DOMAIN_INVESTMENT)
                .version(1)
                .build();
        when(postRepo.findById(103L)).thenReturn(Optional.of(post));

        BizException ex = assertThrows(
                BizException.class,
                () -> service.resolvePendingPostReview(103L, 7L, true, "self review")
        );

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        org.mockito.Mockito.verify(postRepo, org.mockito.Mockito.never())
                .updateStatusIfCurrent(any(), any(), any(), any());
    }

    @Test
    void resolvePendingReviewRejectsAStaleQueueVersion() {
        Post post = Post.builder()
                .id(104L)
                .authorId(7L)
                .postStatus(Post.STATUS_REVIEWING)
                .domain(Post.DOMAIN_INVESTMENT)
                .version(5)
                .build();
        when(postRepo.findById(104L)).thenReturn(Optional.of(post));

        BizException ex = assertThrows(
                BizException.class,
                () -> service.resolvePendingPostReview(104L, 99L, true, "stale", 4)
        );

        assertEquals(ErrorCode.INVALID_STATUS.getCode(), ex.getCode());
        org.mockito.Mockito.verify(postRepo, org.mockito.Mockito.never())
                .updateStatusIfCurrent(any(), any(), any(), any());
    }

    private void arrangeValidUpdate(Long postId) {
        Post post = Post.builder()
                .id(postId)
                .authorId(7L)
                .postType(Post.TYPE_TECH_ARTICLE)
                .title("existing title")
                .content("existing content long enough for the mocked validator")
                .visibility(Post.VIS_PUBLIC)
                .postStatus(Post.STATUS_PUBLISHED)
                .domain(Post.DOMAIN_TECH)
                .version(3)
                .build();
        when(postRepo.findById(postId)).thenReturn(Optional.of(post));
    }

    private TagPO tag(Long id) {
        TagPO tag = new TagPO();
        tag.setId(id);
        tag.setTagName("tag-" + id);
        return tag;
    }
}
