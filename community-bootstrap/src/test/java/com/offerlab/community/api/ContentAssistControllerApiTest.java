package com.offerlab.community.api;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.api.dto.ContentAssistCapabilityDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedResultDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedRequestSummaryDTO;
import com.offerlab.community.post.api.dto.ContentAssistEnhancedStatusDTO;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreDTO;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsDTO;
import com.offerlab.community.post.api.dto.ContentAssistWritingDTO;
import com.offerlab.community.post.controller.ContentAssistController;
import com.offerlab.community.post.application.ContentAssistEnhancedService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentAssistControllerApiTest {

    @Mock
    private ContentAssistService contentAssistService;
    @Mock
    private ContentAssistEnhancedService contentAssistEnhancedService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new ContentAssistController(contentAssistService, contentAssistEnhancedService), jwtService);
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

    @Test
    void enhancedEndpointsRequireLoginAndDisableHttpCachingForSensitiveResults() throws Exception {
        mvc.perform(post("/api/v1/content-assist/enhanced")
                        .header("Idempotency-Key", "content-assist:test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"draft\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        when(jwtService.parseUid("token")).thenReturn(7L);
        when(contentAssistEnhancedService.capability(7L)).thenReturn(ContentAssistCapabilityDTO.builder()
                .available(true)
                .remainingQuota(2L)
                .benefitCode("AI_ASSIST_QUOTA")
                .consumerCode("CONTENT_ASSIST_ENHANCED")
                .build());
        when(contentAssistEnhancedService.enhance(eq(7L), eq("content-assist:test-key"), any()))
                .thenReturn(ContentAssistEnhancedResultDTO.builder()
                        .requestStatus("FALLBACK")
                        .usageStatus("RELEASED")
                        .quotaConsumed(false)
                        .requestFingerprint("a".repeat(64))
                        .build());
        when(contentAssistEnhancedService.status(7L, "content-assist:test-key"))
                .thenReturn(ContentAssistEnhancedStatusDTO.builder()
                        .requestStatus("FALLBACK")
                        .usageStatus("RELEASED")
                        .quotaConsumed(false)
                        .requestFingerprint("a".repeat(64))
                        .build());

        mvc.perform(get("/api/v1/content-assist/capability")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.remainingQuota").value(2));

        mvc.perform(post("/api/v1/content-assist/enhanced")
                        .header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "content-assist:test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":1,\"postType\":10,\"content\":\"draft\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.requestStatus").value("FALLBACK"))
                .andExpect(jsonPath("$.data.usageStatus").value("RELEASED"));

        mvc.perform(get("/api/v1/content-assist/enhanced/status")
                        .header("Authorization", "Bearer token")
                        .param("idempotencyKey", "content-assist:test-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.requestStatus").value("FALLBACK"));
    }

    @Test
    void enhancedRecentRequiresLoginAndDoesNotCacheRecoverySummaries() throws Exception {
        mvc.perform(get("/api/v1/content-assist/enhanced/recent"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        when(jwtService.parseUid("token")).thenReturn(7L);
        when(contentAssistEnhancedService.recent(7L, 2)).thenReturn(List.of(
                ContentAssistEnhancedRequestSummaryDTO.builder()
                        .requestId(101L)
                        .requestStatus("RUNNING")
                        .usageStatus("RESERVED")
                        .quotaConsumed(false)
                        .requestFingerprint("a".repeat(64))
                        .build()));

        mvc.perform(get("/api/v1/content-assist/enhanced/recent")
                        .header("Authorization", "Bearer token")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[0].requestId").value(101))
                .andExpect(jsonPath("$.data[0].requestStatus").value("RUNNING"))
                .andExpect(jsonPath("$.data[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].contentHash").doesNotExist());

        verify(contentAssistEnhancedService).recent(7L, 2);
    }

    @Test
    void enhancedRequestIdStatusRequiresLoginAndDoesNotCacheResult() throws Exception {
        mvc.perform(get("/api/v1/content-assist/enhanced/requests/101/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        when(jwtService.parseUid("token")).thenReturn(7L);
        when(contentAssistEnhancedService.statusByRequestId(7L, 101L))
                .thenReturn(ContentAssistEnhancedStatusDTO.builder()
                        .requestStatus("SUCCEEDED")
                        .usageStatus("CONFIRMED")
                        .quotaConsumed(true)
                        .requestFingerprint("a".repeat(64))
                        .build());

        mvc.perform(get("/api/v1/content-assist/enhanced/requests/101/status")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.requestStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.usageStatus").value("CONFIRMED"));

        verify(contentAssistEnhancedService).statusByRequestId(7L, 101L);
    }
}
