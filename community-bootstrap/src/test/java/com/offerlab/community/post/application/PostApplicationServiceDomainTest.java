package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.post.api.dto.PostCreateCmd;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostUpdateCmd;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.repository.PostRepository;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostTagRefMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
