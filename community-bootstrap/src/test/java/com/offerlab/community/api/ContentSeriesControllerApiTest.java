package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.api.dto.ContentSeriesAddPostCmd;
import com.offerlab.community.post.api.dto.ContentSeriesCreateCmd;
import com.offerlab.community.post.api.dto.ContentSeriesDTO;
import com.offerlab.community.post.api.dto.ContentSeriesProgressDTO;
import com.offerlab.community.post.api.dto.ContentSeriesUpdateCmd;
import com.offerlab.community.post.application.ContentSeriesService;
import com.offerlab.community.post.controller.ContentSeriesController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentSeriesControllerApiTest {

    @Mock
    private ContentSeriesService contentSeriesService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new ContentSeriesController(contentSeriesService), jwtService);
    }

    @Test
    void mySeriesListReturnsOnlyCurrentUsersWorkbenchSeriesWithProgress() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(contentSeriesService.listMine(7L)).thenReturn(List.of(ContentSeriesDTO.builder()
                .id(501L)
                .creatorUid(7L)
                .title("Java 后端系列")
                .description("从基础到工程化")
                .domain(1)
                .coverUrl("https://cdn.example/series/java.png")
                .progress(ContentSeriesProgressDTO.builder()
                        .publishedPostCount(1L)
                        .totalPostCount(2L)
                        .completionRate(50)
                        .build())
                .createTime(LocalDateTime.of(2026, 6, 24, 10, 0))
                .updateTime(LocalDateTime.of(2026, 6, 24, 12, 0))
                .build()));

        mvc.perform(get("/api/v1/content-series")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(501))
                .andExpect(jsonPath("$.data[0].creatorUid").value(7))
                .andExpect(jsonPath("$.data[0].domain").value(1))
                .andExpect(jsonPath("$.data[0].progress.publishedPostCount").value(1))
                .andExpect(jsonPath("$.data[0].progress.totalPostCount").value(2))
                .andExpect(jsonPath("$.data[0].progress.completionRate").value(50));

        verify(contentSeriesService).listMine(7L);
    }

    @Test
    void createSeriesUsesAuthenticatedUserAndRequestBody() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(contentSeriesService.create(any(ContentSeriesCreateCmd.class), eq(7L)))
                .thenReturn(ContentSeriesDTO.builder()
                        .id(601L)
                        .creatorUid(7L)
                        .title("求职复盘系列")
                        .domain(2)
                        .progress(ContentSeriesProgressDTO.builder()
                                .publishedPostCount(0L)
                                .totalPostCount(0L)
                                .completionRate(0)
                                .build())
                        .build());

        mvc.perform(post("/api/v1/content-series")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"求职复盘系列",
                                  "description":"记录面试准备与复盘",
                                  "domain":2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(601))
                .andExpect(jsonPath("$.data.title").value("求职复盘系列"))
                .andExpect(jsonPath("$.data.domain").value(2));

        verify(contentSeriesService).create(argThat(cmd ->
                        "求职复盘系列".equals(cmd.getTitle())
                                && "记录面试准备与复盘".equals(cmd.getDescription())
                                && Integer.valueOf(2).equals(cmd.getDomain())),
                eq(7L));
    }

    @Test
    void updateSeriesSurfacesForbiddenWhenCurrentUserIsNotCreator() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(contentSeriesService.update(eq(88L), any(ContentSeriesUpdateCmd.class), eq(7L)))
                .thenThrow(new BizException(ErrorCode.FORBIDDEN));

        mvc.perform(put("/api/v1/content-series/88")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"无权限更新",
                                  "description":"should fail",
                                  "domain":1
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void addPostSurfacesForbiddenWhenCurrentUserDoesNotOwnThePost() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(contentSeriesService.addPost(eq(88L), any(ContentSeriesAddPostCmd.class), eq(7L)))
                .thenThrow(new BizException(ErrorCode.FORBIDDEN));

        mvc.perform(post("/api/v1/content-series/88/posts")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId":9901
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }
}
