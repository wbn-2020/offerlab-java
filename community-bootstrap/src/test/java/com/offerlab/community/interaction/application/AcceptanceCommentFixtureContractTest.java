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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceCommentFixtureContractTest {

    private static final long POST_ID = 991100000000000010L;
    private static final long AUTHOR_ID = 990000000000000001L;

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
    void demoMigrationSeedsExactlyTwentyOneVisibleRootCommentsIdempotently() throws Exception {
        String migration = Files.readString(
                Path.of("../db/migration/20260812_demo_acceptance_comment_seed.sql"),
                StandardCharsets.UTF_8);
        String manifest = Files.readString(
                Path.of("../db/migration/flyway-manifest.json"),
                StandardCharsets.UTF_8);

        assertTrue(migration.contains("991100000000000010"));
        assertTrue(Pattern.compile("\"version\"\\s*:\\s*\"20260812\\.01\"").matcher(manifest).find());
        assertTrue(Pattern.compile("\"description\"\\s*:\\s*\"demo_acceptance_comment_seed\"").matcher(manifest).find());
        assertTrue(Pattern.compile("\"stream\"\\s*:\\s*\"demo\"").matcher(manifest).find());
        assertTrue(Pattern.compile("\"demo\"\\s*:\\s*\\{[^}]*\"autoMigrate\"\\s*:\\s*false", Pattern.DOTALL).matcher(manifest).find());
        assertTrue(manifest.contains("db/flyway/demo/V20260812.01__demo_acceptance_comment_seed.sql"));
        assertFalse(manifest.contains("db/flyway/core/V20260812.01__demo_acceptance_comment_seed.sql"));
        Matcher fixtureRows = Pattern.compile(
                "(?m)^    \\(9911200000000000\\d{2}, @acceptance_comment_post_id,")
                .matcher(migration);
        assertEquals(21, countMatches(fixtureRows));
        assertTrue(migration.contains("id BETWEEN 991120000000000001 AND 991120000000000021"));
        assertTrue(migration.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(migration.contains("comment_status = VALUES(comment_status)"));
        assertTrue(migration.contains("is_deleted = VALUES(is_deleted)"));
        assertTrue(migration.contains("SELECT COUNT(*)"));
        assertTrue(migration.contains("comment_status = 1"));
        assertTrue(migration.contains("is_deleted = 0"));
        assertFalse(migration.contains("DELETE FROM"));
        assertFalse(migration.contains("TRUNCATE"));
    }

    @Test
    void latestCommentsExposeTwentyOneTotalAcrossTwoDeterministicPages() {
        stubPostVisible();
        List<CommentPO> comments = latestComments();
        stubCommentProjection();
        when(commentMapper.countVisibleComments(POST_ID)).thenReturn(21L);
        when(commentMapper.selectList(any()))
                .thenReturn(comments.subList(0, 21))
                .thenReturn(comments.subList(20, 21));

        PageResult<CommentDTO> first = facade.listComments(POST_ID, null, "0", 20, "latest");
        PageResult<CommentDTO> second = facade.listComments(
                POST_ID, null, first.getNextCursor(), 20, "latest");

        assertEquals(21L, first.getTotal());
        assertEquals(20, first.getItems().size());
        assertTrue(first.getHasMore());
        assertNotNull(first.getNextCursor());
        assertEquals(991120000000000021L, first.getItems().get(0).getId());
        assertEquals(991120000000000002L, first.getItems().get(19).getId());
        assertEquals(21L, second.getTotal());
        assertEquals(List.of(991120000000000001L),
                second.getItems().stream().map(CommentDTO::getId).toList());
        assertFalse(second.getHasMore());
    }

    @Test
    void successfulZeroStateIsDistinctFromACommentReadFailure() {
        stubPostVisible();
        when(commentMapper.countVisibleComments(POST_ID)).thenReturn(0L, 21L);
        when(commentMapper.selectList(any())).thenReturn(List.of(), List.of());

        PageResult<CommentDTO> empty = facade.listComments(POST_ID, null, "0", 20, "latest");
        BizException failure = assertThrows(BizException.class,
                () -> facade.listComments(POST_ID, null, "0", 20, "latest"));

        assertEquals(0L, empty.getTotal());
        assertEquals(List.of(), empty.getItems());
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), failure.getCode());
        assertEquals("评论数据暂时无法读取，请稍后重试", failure.getMessage());
    }

    @Test
    void qualitySortUsesItsOwnCursorAndPreservesMapperRanking() {
        stubPostVisible();
        List<CommentPO> ranked = List.of(
                comment(991120000000000003L, 3, 6, LocalDateTime.of(2026, 7, 21, 8, 10)),
                comment(991120000000000020L, 6, 1, LocalDateTime.of(2026, 7, 21, 9, 35)),
                comment(991120000000000015L, 1, 7, LocalDateTime.of(2026, 7, 21, 9, 10)));
        stubCommentProjection();
        when(commentMapper.countVisibleComments(POST_ID)).thenReturn(21L);
        when(commentMapper.selectQualityRoots(
                eq(POST_ID), any(), any(), any(), any(), any(), any(), any(), eq(3)))
                .thenReturn(ranked);

        PageResult<CommentDTO> page = facade.listComments(POST_ID, null, "0", 2, "quality");

        assertEquals(List.of(991120000000000003L, 991120000000000020L),
                page.getItems().stream().map(CommentDTO::getId).toList());
        assertTrue(page.getHasMore());
        String qualityCursor = new String(
                Base64.getUrlDecoder().decode(page.getNextCursor()),
                StandardCharsets.UTF_8);
        assertTrue(qualityCursor.startsWith("cmq1|"));
        assertEquals(21L, page.getTotal());
        verify(commentMapper).selectQualityRoots(
                eq(POST_ID), any(), any(), any(), any(), any(), any(), any(), eq(3));
    }

    @Test
    void aFailedReadCanBeRetriedWithoutStickyBackendState() {
        stubPostVisible();
        CommentPO recovered = comment(
                991120000000000021L, 0, 3, LocalDateTime.of(2026, 7, 21, 9, 40));
        stubCommentProjection();
        when(commentMapper.countVisibleComments(POST_ID)).thenReturn(21L);
        when(commentMapper.selectList(any()))
                .thenThrow(new IllegalStateException("temporary database read failure"))
                .thenReturn(List.of(recovered));

        assertThrows(IllegalStateException.class,
                () -> facade.listComments(POST_ID, null, "0", 20, "latest"));
        PageResult<CommentDTO> retry = facade.listComments(POST_ID, null, "0", 20, "latest");

        assertEquals(21L, retry.getTotal());
        assertEquals(List.of(991120000000000021L),
                retry.getItems().stream().map(CommentDTO::getId).toList());
        verify(commentMapper, times(2)).selectList(any());
    }

    private void stubPostVisible() {
        when(postFacade.getPost(eq(POST_ID), any()))
                .thenReturn(PostDTO.builder().id(POST_ID).authorId(AUTHOR_ID).build());
    }

    private void stubCommentProjection() {
        when(commentMapper.selectPreviewRepliesByRootIds(eq(POST_ID), any(), anyInt()))
                .thenReturn(List.of());
        when(commentMapper.countRepliesByRootIds(eq(POST_ID), any())).thenReturn(List.of());
        when(commentQualitySignalMapper.selectActiveByCommentIds(any())).thenReturn(List.of());
        when(userFacade.batchGetUserBriefs(any(Set.class))).thenReturn(Map.of(
                AUTHOR_ID, UserBriefDTO.builder().uid(AUTHOR_ID).nickname("验收作者").build()));
    }

    private static List<CommentPO> latestComments() {
        List<CommentPO> comments = new ArrayList<>();
        LocalDateTime base = LocalDateTime.of(2026, 7, 21, 8, 0);
        for (int sequence = 21; sequence >= 1; sequence--) {
            comments.add(comment(
                    991120000000000000L + sequence,
                    sequence % 7,
                    sequence % 8,
                    base.plusMinutes((sequence - 1L) * 5L)));
        }
        return comments;
    }

    private static CommentPO comment(long id, int helpfulCount, int likeCount, LocalDateTime createdAt) {
        CommentPO comment = new CommentPO();
        comment.setId(id);
        comment.setPostId(POST_ID);
        comment.setPostAuthorId(AUTHOR_ID);
        comment.setAuthorId(AUTHOR_ID);
        comment.setRootId(0L);
        comment.setParentId(0L);
        comment.setContent("acceptance comment " + id);
        comment.setLikeCount(likeCount);
        comment.setHelpfulCount(helpfulCount);
        comment.setCommentStatus(1);
        comment.setIsDeleted(0);
        comment.setCreateTime(createdAt);
        comment.setUpdateTime(createdAt);
        return comment;
    }

    private static int countMatches(Matcher matcher) {
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
