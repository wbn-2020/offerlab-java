package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreDTO;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsDTO;
import com.offerlab.community.post.api.dto.ContentAssistWritingDTO;
import com.offerlab.community.post.controller.ContentAssistController;
import com.offerlab.community.post.application.ContentAssistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentAssistControllerApiTest {

    @Mock
    private ContentAssistService contentAssistService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new ContentAssistController(contentAssistService), jwtService);
    }

    @Test
    void contentAssistWritingRequiresLogin() throws Exception {
        mvc.perform(post("/api/v1/content-assist/writing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spring Cloud 鉴权链路\",\"content\":\"draft\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(contentAssistService);
    }

    @Test
    void authenticatedWritingReturnsFallbackMetadataAndSuggestions() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(7L);
        when(contentAssistService.assistWriting(eq(7L), any())).thenReturn(ContentAssistWritingDTO.builder()
                .provider("rules")
                .fallbackUsed(false)
                .promptTokens(0)
                .completionTokens(0)
                .estimatedCostMicros(0L)
                .suggestedTitle("Spring Cloud 鉴权链路排查复盘")
                .summary("建议先交代业务背景，再展开排查链路。")
                .outline(List.of("背景", "问题定位", "解决方案", "结果复盘"))
                .suggestions(List.of("补充监控指标", "解释取舍"))
                .riskHints(List.of("AI 建议不替代内容审核"))
                .build());

        mvc.perform(post("/api/v1/content-assist/writing")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": 1,
                                  "postType": 10,
                                  "title": "Spring Cloud 鉴权链路",
                                  "content": "网关鉴权链路排查记录"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("rules"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(false))
                .andExpect(jsonPath("$.data.suggestedTitle").value("Spring Cloud 鉴权链路排查复盘"))
                .andExpect(jsonPath("$.data.outline[0]").value("背景"))
                .andExpect(jsonPath("$.data.riskHints[0]").value("AI 建议不替代内容审核"));

        verify(contentAssistService).assistWriting(eq(7L), any());
    }

    @Test
    void authenticatedQualityScoreReturnsExplainableAdvisoryDto() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(9L);
        when(contentAssistService.scoreQuality(eq(9L), any())).thenReturn(ContentAssistQualityScoreDTO.builder()
                .score(78)
                .level("GOOD")
                .advisoryOnly(true)
                .summary("结构比较清晰，但可以继续补充结果指标。")
                .suggestions(List.of("增加结果数据", "补充边界条件"))
                .explanations(List.of(
                        ContentAssistQualityScoreDTO.DimensionDTO.builder()
                                .dimension("content")
                                .score(32)
                                .reason("正文已经覆盖问题与方案，但结果量化不足。")
                                .build(),
                        ContentAssistQualityScoreDTO.DimensionDTO.builder()
                                .dimension("structure")
                                .score(20)
                                .reason("有明确的排查顺序。")
                                .build()))
                .provider("rules")
                .fallbackUsed(false)
                .promptTokens(0)
                .completionTokens(0)
                .estimatedCostMicros(0L)
                .build());

        mvc.perform(post("/api/v1/content-assist/quality-score")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": 1,
                                  "postType": 10,
                                  "title": "Spring Cloud 鉴权链路排查",
                                  "content": "先排查网关配置，再定位 token 透传问题。",
                                  "tagNames": ["Spring Cloud", "Gateway"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.score").value(78))
                .andExpect(jsonPath("$.data.advisoryOnly").value(true))
                .andExpect(jsonPath("$.data.explanations[0].dimension").value("content"))
                .andExpect(jsonPath("$.data.provider").value("rules"));

        verify(contentAssistService).scoreQuality(eq(9L), any());
    }

    @Test
    void authenticatedTagTopicSuggestionsReuseExistingTaxonomyDtos() throws Exception {
        when(jwtService.parseUid("token")).thenReturn(12L);
        when(contentAssistService.suggestTagsAndTopics(eq(12L), any())).thenReturn(ContentAssistTagTopicSuggestionsDTO.builder()
                .provider("rules")
                .fallbackUsed(false)
                .domain(1)
                .domainName("技术")
                .tags(List.of(
                        ContentAssistTagTopicSuggestionsDTO.TagSuggestionDTO.builder()
                                .id(101L)
                                .name("Spring Boot")
                                .reason("正文命中了现有标签关键词")
                                .official(true)
                                .recommended(true)
                                .build(),
                        ContentAssistTagTopicSuggestionsDTO.TagSuggestionDTO.builder()
                                .id(102L)
                                .name("Redis")
                                .reason("正文命中了现有标签关键词")
                                .official(false)
                                .recommended(true)
                                .build()))
                .topics(List.of(
                        ContentAssistTagTopicSuggestionsDTO.TopicSuggestionDTO.builder()
                                .id(301L)
                                .slug("backend-infra")
                                .name("后端基础设施")
                                .reason("匹配到已存在专题标签")
                                .virtualTopic(false)
                                .build()))
                .build());

        mvc.perform(post("/api/v1/content-assist/tag-topic-suggestions")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "domain": 1,
                                  "title": "Spring Boot + Redis 链路压测复盘",
                                  "content": "重点分析 Spring Boot、Redis 和缓存回源链路。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("rules"))
                .andExpect(jsonPath("$.data.domainName").value("技术"))
                .andExpect(jsonPath("$.data.tags[0].name").value("Spring Boot"))
                .andExpect(jsonPath("$.data.topics[0].slug").value("backend-infra"));

        verify(contentAssistService).suggestTagsAndTopics(eq(12L), any());
    }
}
