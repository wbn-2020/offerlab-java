package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.application.PostApplicationService;
import com.offerlab.community.post.application.PostDraftService;
import com.offerlab.community.post.application.PostFeaturedService;
import com.offerlab.community.post.application.PostKnowledgeReviewService;
import com.offerlab.community.post.application.PostReportService;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.controller.PostController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerApiTest {
    @Mock
    private PostFacade postFacade;
    @Mock
    private PostApplicationService postService;
    @Mock
    private PostReportService reportService;
    @Mock
    private PostFeaturedService featuredService;
    @Mock
    private PostKnowledgeReviewService knowledgeReviewService;
    @Mock
    private PostDraftService draftService;
    @Mock
    private DomainModeratorService domainModeratorService;
    @Mock
    private AdminPermissionService adminPermissionService;
    @Mock
    private ContentModerationService contentModerationService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(
                new PostController(postFacade, postService, reportService, featuredService, knowledgeReviewService,
                        draftService, domainModeratorService, adminPermissionService, contentModerationService),
                jwtService);
    }

    @Test
    void missingPostDetailReturns404InsteadOfSuccessNullData() throws Exception {
        when(postFacade.getPost(99L)).thenReturn(null);

        mvc.perform(get("/api/v1/posts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.POST_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.POST_NOT_FOUND.getMessage()));

        verify(postFacade).getPost(99L);
        verifyNoInteractions(postService);
    }

    @Test
    void contentTypesExposeCommunityAndLegacyTypeDirectory() throws Exception {
        mvc.perform(get("/api/v1/posts/content-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].value").value(10))
                .andExpect(jsonPath("$.data[0].code").value("TECH_ARTICLE"))
                .andExpect(jsonPath("$.data[0].label").value("技术文章"))
                .andExpect(jsonPath("$.data[0].minContentLength").value(40))
                .andExpect(jsonPath("$.data[8].value").value(1))
                .andExpect(jsonPath("$.data[8].legacy").value(true));

        verifyNoInteractions(postFacade, postService);
    }

    @Test
    void communityTypeListPassesTypeFilterToFacade() throws Exception {
        PostBriefDTO post = PostBriefDTO.builder()
                .id(8001L)
                .postType(10)
                .title("TECH_ARTICLE list recall")
                .summary("regular community content")
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.listPosts(null, null, 10, null, null, 0L, 20, false))
                .thenReturn(PageResult.of(List.of(post), null, false));

        mvc.perform(get("/api/v1/posts")
                        .param("type", "10")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(8001))
                .andExpect(jsonPath("$.data.items[0].postType").value(10));

        verify(postFacade).listPosts(null, null, 10, null, null, 0L, 20, false);
    }

    @Test
    void invalidListDomainReturnsParamErrorBeforeFacade() throws Exception {
        mvc.perform(get("/api/v1/posts")
                        .param("domain", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(postFacade);
    }

    @Test
    void postListPassesDomainFilterAndPaginationMetadataToFacade() throws Exception {
        PostBriefDTO post = PostBriefDTO.builder()
                .id(8102L)
                .postType(10)
                .domain(2)
                .title("career domain post")
                .summary("domain filtered content")
                .createTime(LocalDateTime.now())
                .build();
        when(postFacade.listPosts(null, null, null, null, 2, 123L, 2, false))
                .thenReturn(PageResult.of(List.of(post), "456", true));

        mvc.perform(get("/api/v1/posts")
                        .param("domain", "2")
                        .param("cursor", "123")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(8102))
                .andExpect(jsonPath("$.data.items[0].domain").value(2))
                .andExpect(jsonPath("$.data.nextCursor").value("456"))
                .andExpect(jsonPath("$.data.hasMore").value(true));

        verify(postFacade).listPosts(null, null, null, null, 2, 123L, 2, false);
    }

    @Test
    void invalidPublishDomainReturnsParamErrorBeforePublishing() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);

        mvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postType\":10,\"domain\":999,\"title\":\"bad domain\",\"content\":\"content\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));

        verifyNoInteractions(postFacade, postService, draftService);
    }
}
