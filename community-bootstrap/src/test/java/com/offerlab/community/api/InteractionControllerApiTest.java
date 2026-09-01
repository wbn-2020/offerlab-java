package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.interaction.api.DiscussionFollowFacade;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.api.dto.CommentDTO;
import com.offerlab.community.interaction.application.CommentReportService;
import com.offerlab.community.interaction.controller.InteractionController;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.application.DomainModeratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InteractionControllerApiTest {
    @Mock
    private InteractionFacade facade;
    @Mock
    private PostFacade postFacade;
    @Mock
    private DiscussionFollowFacade discussionFollowFacade;
    @Mock
    private CommentReportService reportService;
    @Mock
    private DomainModeratorService domainModeratorService;
    @Mock
    private ContentModerationService contentModerationService;
    @Mock
    private SnowflakeIdGenerator idGenerator;
    @Mock
    private JwtService jwtService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        mvc = ApiTestSupport.mvc(
                new InteractionController(facade, postFacade, discussionFollowFacade, reportService,
                        domainModeratorService, contentModerationService, idGenerator, redis),
                jwtService);
    }

    @Test
    void commentsArePublicAndUseAnonymousViewerWhenNoToken() throws Exception {
        when(facade.listComments(10L, null, "0", 20, "latest")).thenReturn(PageResult.empty());
        when(facade.getCommentContext(10L, 99L, null))
                .thenReturn(CommentDTO.builder().id(99L).postId(10L).build());

        mvc.perform(get("/api/v1/posts/10/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/v1/posts/10/comments/99/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(99));

        verify(facade).listComments(10L, null, "0", 20, "latest");
        verify(facade).getCommentContext(10L, 99L, null);
    }

    @Test
    void protectedLikeRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/posts/10/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(facade);
    }

    @Test
    void commentUsesAuthenticatedUserAndModerationDecision() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(99L);
        when(idGenerator.nextId()).thenReturn(77L);
        when(valueOperations.setIfAbsent(anyString(), eq("77"), any())).thenReturn(true);
        when(contentModerationService.checkContent(eq(99L), eq(ContentModerationService.SCOPE_COMMENT),
                eq(ContentModerationService.SOURCE_COMMENT), eq(77L), eq("hello")))
                .thenReturn(new ContentModerationService.ModerationDecision(false, "ALLOW", null, null));

        mvc.perform(post("/api/v1/posts/10/comments")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.commentId").value(77))
                .andExpect(jsonPath("$.data.reviewRequired").value(false));

        ArgumentCaptor<CommentCreateCmd> captor = ArgumentCaptor.forClass(CommentCreateCmd.class);
        verify(facade).addComment(captor.capture());
        assertEquals(77L, captor.getValue().getCommentId());
        assertEquals(10L, captor.getValue().getPostId());
        assertEquals(99L, captor.getValue().getAuthorUid());
        assertEquals("hello", captor.getValue().getContent());
        assertEquals(false, captor.getValue().getReviewRequired());
    }

    @Test
    void repeatedCommentReturnsOriginalIdWithoutSecondWrite() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(99L);
        when(idGenerator.nextId()).thenReturn(78L);
        when(valueOperations.get(anyString())).thenReturn("77");

        mvc.perform(post("/api/v1/posts/10/comments")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value(77))
                .andExpect(jsonPath("$.data.deduplicated").value(true));

        verifyNoInteractions(facade);
    }

    @Test
    void concurrentReservationWithoutReadableIdDoesNotInsertDuplicate() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(99L);
        when(idGenerator.nextId()).thenReturn(78L);
        when(valueOperations.setIfAbsent(anyString(), eq("78"), any())).thenReturn(false);

        mvc.perform(post("/api/v1/posts/10/comments")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_MODIFICATION.getCode()))
                .andExpect(jsonPath("$.message").value("评论正在提交，请稍后查看结果"));

        verifyNoInteractions(facade);
    }

    @Test
    void unavailableCommentIdempotencyStoreFailsClosed() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(99L);
        when(idGenerator.nextId()).thenReturn(78L);
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis unavailable"));

        mvc.perform(post("/api/v1/posts/10/comments")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.CACHE_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试。"));

        verifyNoInteractions(facade);
    }

    @Test
    void domainModeratorCanListCommentReportsForOwnDomain() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(88L);

        mvc.perform(get("/api/v1/comments/admin/reports")
                        .header("Authorization", "Bearer token")
                        .param("domain", "2")
                        .param("status", "0")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(domainModeratorService).requireModerateDomain(88L, 2);
        verify(reportService).listRecent(0, 2, 10, false);
    }

    @Test
    void commentReportReviewDelegatesDomainAuthorizationToService() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(88L);

        mvc.perform(post("/api/v1/comments/admin/reports/42/review")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"note\":\"not actionable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(reportService).reviewReport(42L, 88L, false, "not actionable");
    }
}
