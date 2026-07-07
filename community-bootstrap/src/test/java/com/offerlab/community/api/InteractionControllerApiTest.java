package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.interaction.api.DiscussionFollowFacade;
import com.offerlab.community.interaction.api.InteractionFacade;
import com.offerlab.community.interaction.api.dto.CommentCreateCmd;
import com.offerlab.community.interaction.application.CommentReportService;
import com.offerlab.community.interaction.controller.InteractionController;
import com.offerlab.community.post.application.DomainModeratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private DiscussionFollowFacade discussionFollowFacade;
    @Mock
    private CommentReportService reportService;
    @Mock
    private DomainModeratorService domainModeratorService;
    @Mock
    private ContentModerationService contentModerationService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(
                new InteractionController(facade, discussionFollowFacade, reportService, domainModeratorService, contentModerationService),
                jwtService);
    }

    @Test
    void commentsArePublicAndUseAnonymousViewerWhenNoToken() throws Exception {
        when(facade.listComments(10L, null, 0L, 20, "latest")).thenReturn(PageResult.empty());

        mvc.perform(get("/api/v1/posts/10/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(facade).listComments(10L, null, 0L, 20, "latest");
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
        when(contentModerationService.checkContent(eq(99L), eq(ContentModerationService.SCOPE_COMMENT), eq("hello")))
                .thenReturn(new ContentModerationService.ModerationDecision(false, "ALLOW", null, null));
        when(facade.addComment(any(CommentCreateCmd.class))).thenReturn(77L);

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
        assertEquals(10L, captor.getValue().getPostId());
        assertEquals(99L, captor.getValue().getAuthorUid());
        assertEquals("hello", captor.getValue().getContent());
        assertEquals(false, captor.getValue().getReviewRequired());
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
