package com.offerlab.community.interaction.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.redis.cache.PostCounterRedis;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentHelpfulMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.CommentQualitySignalMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.FavoriteFolderMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.FavoriteMapper;
import com.offerlab.community.interaction.infrastructure.persistence.mapper.LikeMapper;
import com.offerlab.community.interaction.infrastructure.persistence.po.CommentPO;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostCounterMapper;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionFacadeVisibilityTest {
    @Mock
    private LikeMapper likeMapper;
    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private FavoriteFolderMapper favoriteFolderMapper;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private CommentHelpfulMapper commentHelpfulMapper;
    @Mock
    private CommentQualitySignalMapper commentQualitySignalMapper;
    @Mock
    private PostCounterMapper postCounterMapper;
    @Mock
    private PostCounterRedis postCounterRedis;
    @Mock
    private PostFacade postFacade;
    @Mock
    private UserFacade userFacade;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private SnowflakeIdGenerator idGen;
    @Mock
    private EventPublisher events;
    @Mock
    private AfterCommitExecutor afterCommit;

    private InteractionFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new InteractionFacadeImpl(
                likeMapper,
                favoriteMapper,
                favoriteFolderMapper,
                commentMapper,
                commentHelpfulMapper,
                commentQualitySignalMapper,
                postCounterMapper,
                postCounterRedis,
                postFacade,
                userFacade,
                adminPermissionService,
                idGen,
                events,
                afterCommit);
    }

    @Test
    void listCommentsStopsBeforeQueryingCommentsWhenPostIsNotVisible() {
        when(postFacade.getPost(100L, null)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> facade.listComments(100L, null, 0L, 20));

        assertEquals(ErrorCode.POST_NOT_FOUND.getCode(), error.getCode());
        verify(postFacade).getPost(100L, null);
        verify(commentMapper, never()).selectList(any());
    }

    @Test
    void listCommentsUsesTheSameViewerAsPostDetailVisibility() {
        when(postFacade.getPost(100L, 20L)).thenReturn(null);

        BizException error = assertThrows(BizException.class,
                () -> facade.listComments(100L, 20L, 0L, 20));

        assertEquals(ErrorCode.POST_NOT_FOUND.getCode(), error.getCode());
        verify(postFacade).getPost(100L, 20L);
        verify(commentMapper, never()).selectList(any());
    }

    @Test
    void listCommentsReturnsSuccessfulZeroStateWhenNoVisibleCommentsExist() {
        when(postFacade.getPost(100L, null)).thenReturn(PostDTO.builder().id(100L).build());
        when(commentMapper.countVisibleComments(100L)).thenReturn(0L);
        when(commentMapper.selectList(any())).thenReturn(List.of());

        PageResult<CommentDTO> page = facade.listComments(100L, null, "0", 20, "latest");

        assertEquals(List.of(), page.getItems());
        assertEquals(0L, page.getTotal());
        assertFalse(page.getHasMore());
    }

    @Test
    void listCommentsRejectsNonzeroTotalWithEmptyFirstPageAsDataFailure() {
        when(postFacade.getPost(100L, null)).thenReturn(PostDTO.builder().id(100L).build());
        when(commentMapper.countVisibleComments(100L)).thenReturn(21L);
        when(commentMapper.selectList(any())).thenReturn(List.of());

        BizException error = assertThrows(
                BizException.class,
                () -> facade.listComments(100L, null, "0", 20, "latest"));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), error.getCode());
        assertEquals("评论数据暂时无法读取，请稍后重试", error.getMessage());
    }

    @Test
    void listCommentsKeepsRealTotalWhenAnLaterPageHasNoRows() {
        when(postFacade.getPost(100L, null)).thenReturn(PostDTO.builder().id(100L).build());
        when(commentMapper.countVisibleComments(100L)).thenReturn(21L);
        when(commentMapper.selectList(any())).thenReturn(List.of());

        PageResult<CommentDTO> page = facade.listComments(100L, null, "1710000000000:99", 20, "latest");

        assertEquals(List.of(), page.getItems());
        assertEquals(21L, page.getTotal());
        assertFalse(page.getHasMore());
    }

    @Test
    void authorCanReadVisibleOwnPostComments() {
        CommentPO root = new CommentPO();
        root.setId(1L);
        root.setPostId(100L);
        root.setPostAuthorId(10L);
        root.setAuthorId(10L);
        root.setRootId(0L);
        root.setParentId(0L);
        root.setContent("hello");
        root.setLikeCount(0);
        root.setHelpfulCount(10);
        root.setCommentStatus(1);
        root.setCreateTime(LocalDateTime.now());

        CommentPO reply = new CommentPO();
        reply.setId(2L);
        reply.setPostId(100L);
        reply.setPostAuthorId(10L);
        reply.setAuthorId(10L);
        reply.setRootId(1L);
        reply.setParentId(1L);
        reply.setContent("follow-up");
        reply.setLikeCount(0);
        reply.setCommentStatus(1);
        reply.setCreateTime(root.getCreateTime().plusSeconds(1));

        CommentPO newestRegularRoot = new CommentPO();
        newestRegularRoot.setId(3L);
        newestRegularRoot.setPostId(100L);
        newestRegularRoot.setPostAuthorId(10L);
        newestRegularRoot.setAuthorId(10L);
        newestRegularRoot.setRootId(0L);
        newestRegularRoot.setParentId(0L);
        newestRegularRoot.setContent("newest regular");
        newestRegularRoot.setLikeCount(0);
        newestRegularRoot.setHelpfulCount(0);
        newestRegularRoot.setCommentStatus(1);
        newestRegularRoot.setCreateTime(root.getCreateTime().plusSeconds(2));

        CommentPO middleRegularRoot = new CommentPO();
        middleRegularRoot.setId(4L);
        middleRegularRoot.setPostId(100L);
        middleRegularRoot.setPostAuthorId(10L);
        middleRegularRoot.setAuthorId(10L);
        middleRegularRoot.setRootId(0L);
        middleRegularRoot.setParentId(0L);
        middleRegularRoot.setContent("middle regular");
        middleRegularRoot.setLikeCount(0);
        middleRegularRoot.setHelpfulCount(0);
        middleRegularRoot.setCommentStatus(1);
        middleRegularRoot.setCreateTime(root.getCreateTime().plusSeconds(1));

        List<CommentPO> qualityRankedRoots = List.of(root, newestRegularRoot, middleRegularRoot);
        when(postFacade.getPost(100L, 10L)).thenReturn(PostDTO.builder().id(100L).authorId(10L).build());
        when(commentMapper.countVisibleComments(100L)).thenReturn(2L);
        when(commentMapper.selectList(any())).thenReturn(List.of(root), List.of());
        when(commentMapper.selectQualityRoots(eq(100L), any(), any(), any(), any(), any(), any(), any(), eq(3)))
                .thenReturn(qualityRankedRoots, List.of(middleRegularRoot));
        when(commentMapper.selectById(1L)).thenReturn(root);
        when(commentMapper.selectById(2L)).thenReturn(reply);
        when(commentMapper.countRepliesByRootIds(100L, List.of(1L)))
                .thenReturn(List.of(Map.of("rootId", 1L, "replyCount", 1L)));
        when(userFacade.batchGetUserBriefs(any(Set.class))).thenReturn(Map.of(
                10L, UserBriefDTO.builder().uid(10L).nickname("author").build()));
        when(likeMapper.selectActiveTargetIdsByUser(10L, 2, List.of(1L))).thenReturn(List.of());

        PageResult<CommentDTO> page = facade.listComments(100L, 10L, 0L, 20);
        CommentDTO context = facade.getCommentContext(100L, 2L, 10L);
        assertThrows(BizException.class, () -> facade.listComments(100L, 10L, "1", 2, "quality"));
        PageResult<CommentDTO> firstQualityPage = facade.listComments(100L, 10L, "0", 2, "quality");
        PageResult<CommentDTO> secondQualityPage = facade.listComments(
                100L, 10L, firstQualityPage.getNextCursor(), 2, "quality");

        assertEquals(1, page.getItems().size());
        assertEquals(2L, page.getTotal());
        assertEquals(1L, page.getItems().get(0).getId());
        assertEquals("hello", page.getItems().get(0).getContent());
        assertFalse(page.getItems().get(0).getMyLiked());
        assertEquals(1L, context.getId());
        assertEquals(1, context.getReplies().size());
        assertEquals(2L, context.getReplies().get(0).getId());
        assertEquals("follow-up", context.getReplies().get(0).getContent());
        assertEquals(List.of(1L, 3L), firstQualityPage.getItems().stream().map(CommentDTO::getId).toList());
        assertEquals(List.of(4L), secondQualityPage.getItems().stream().map(CommentDTO::getId).toList());
        verify(postFacade, times(5)).getPost(100L, 10L);
        verify(commentMapper, times(2)).selectQualityRoots(eq(100L), any(), any(), any(), any(), any(), any(), any(), eq(3));
        verify(commentMapper, atLeastOnce()).selectList(any());
    }
}
