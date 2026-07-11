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
        root.setCommentStatus(1);
        root.setCreateTime(LocalDateTime.now());

        when(postFacade.getPost(100L, 10L)).thenReturn(PostDTO.builder().id(100L).authorId(10L).build());
        when(commentMapper.selectList(any())).thenReturn(List.of(root), List.of());
        when(userFacade.batchGetUserBriefs(any(Set.class))).thenReturn(Map.of(
                10L, UserBriefDTO.builder().uid(10L).nickname("author").build()));
        when(likeMapper.selectActiveTargetIdsByUser(10L, 2, List.of(1L))).thenReturn(List.of());

        PageResult<CommentDTO> page = facade.listComments(100L, 10L, 0L, 20);

        assertEquals(1, page.getItems().size());
        assertEquals(1L, page.getItems().get(0).getId());
        assertEquals("hello", page.getItems().get(0).getContent());
        assertFalse(page.getItems().get(0).getMyLiked());
        verify(postFacade).getPost(100L, 10L);
        verify(commentMapper, atLeastOnce()).selectList(any());
    }
}
