package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.CommunityTopicDTO;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreCmd;
import com.offerlab.community.post.api.dto.ContentAssistQualityScoreDTO;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsCmd;
import com.offerlab.community.post.api.dto.ContentAssistTagTopicSuggestionsDTO;
import com.offerlab.community.post.api.dto.ContentAssistWritingCmd;
import com.offerlab.community.post.api.dto.ContentAssistWritingDTO;
import com.offerlab.community.post.api.dto.TagDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentAssistServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aiDefaultOffUsesRuleFallbackAndOnlyRecordsMetadata() {
        InMemoryRecordGateway records = new InMemoryRecordGateway();
        StubAiClient aiClient = StubAiClient.disabled();
        ContentAssistService service = new ContentAssistService(
                objectMapper,
                aiClient,
                new StubTaxonomyService(List.of(), List.of()),
                records);

        String content = "Spring Cloud Gateway 鉴权链路排查记录，包含背景、定位过程、解决方案与结果复盘。";
        ContentAssistWritingDTO result = service.assistWriting(7L, ContentAssistWritingCmd.builder()
                .domain(1)
                .postType(10)
                .title("Spring Cloud 鉴权链路排查")
                .content(content)
                .build());

        assertEquals("rules", result.getProvider());
        assertFalse(result.getFallbackUsed());
        assertEquals(0, aiClient.calls);
        assertEquals(1, records.records.size());
        assertEquals("WRITING", records.last().scene());
        assertEquals("RULE_ONLY", records.last().status());
        assertEquals(content.length(), records.last().contentLength());
        assertNotNull(records.last().contentHash());
        assertFalse(records.last().contentHash().isBlank());
        assertEquals(0, records.last().promptTokens());
        assertTrue(result.getSuggestions().size() > 0);
    }

    @Test
    void invalidAiJsonFallsBackToRulesAndCapturesErrorCode() {
        InMemoryRecordGateway records = new InMemoryRecordGateway();
        StubAiClient aiClient = StubAiClient.withResponse("""
                {"suggestedTitle":12,"outline":"bad-type","extra":"drop-me"}
                """);
        ContentAssistService service = new ContentAssistService(
                objectMapper,
                aiClient,
                new StubTaxonomyService(List.of(), List.of()),
                records);

        ContentAssistWritingDTO result = service.assistWriting(9L, ContentAssistWritingCmd.builder()
                .domain(1)
                .postType(10)
                .title("Redis 热点 key 重建复盘")
                .content("Redis 热点 key 失效后，先回源数据库，再补互斥锁与空值缓存。")
                .build());

        assertEquals("rules", result.getProvider());
        assertTrue(result.getFallbackUsed());
        assertEquals("AI_INVALID_RESPONSE", result.getErrorCode());
        assertEquals("AI_FALLBACK", records.last().status());
        assertEquals("AI_INVALID_RESPONSE", records.last().errorCode());
    }

    @Test
    void aiFailureFallsBackToRulesWithoutBreakingAssistFlow() {
        InMemoryRecordGateway records = new InMemoryRecordGateway();
        StubAiClient aiClient = StubAiClient.withFailure(new IllegalStateException("request timeout"));
        ContentAssistService service = new ContentAssistService(
                objectMapper,
                aiClient,
                new StubTaxonomyService(List.of(), List.of()),
                records);

        ContentAssistWritingDTO result = service.assistWriting(15L, ContentAssistWritingCmd.builder()
                .domain(1)
                .postType(10)
                .title("gateway timeout fallback")
                .content("trace timeout, retry, and recover with cached rules")
                .build());

        assertEquals("rules", result.getProvider());
        assertTrue(result.getFallbackUsed());
        assertEquals("AI_TIMEOUT", result.getErrorCode());
        assertEquals(1, aiClient.calls);
        assertEquals("AI_FALLBACK", records.last().status());
        assertEquals("deepseek", records.last().provider());
        assertEquals("AI_TIMEOUT", records.last().errorCode());
    }

    @Test
    void qualityScoreIsExplainableAndMarkedAsAdvisoryOnly() {
        ContentAssistService service = new ContentAssistService(
                objectMapper,
                StubAiClient.disabled(),
                new StubTaxonomyService(List.of(), List.of()),
                new InMemoryRecordGateway());

        ContentAssistQualityScoreDTO result = service.scoreQuality(11L, ContentAssistQualityScoreCmd.builder()
                .domain(1)
                .postType(10)
                .title("Spring Cloud 鉴权链路排查")
                .content("""
                        背景：线上网关鉴权偶发失败。
                        过程：先检查 token 透传，再对比下游服务日志。
                        方案：修复 header 丢失并补充熔断保护。
                        结果：错误率下降，链路恢复稳定。
                        """)
                .tagNames(List.of("Spring Cloud", "Gateway"))
                .build());

        assertTrue(result.getAdvisoryOnly());
        assertTrue(result.getScore() > 0);
        assertFalse(result.getExplanations().isEmpty());
        assertTrue(result.getExplanations().stream().anyMatch(item -> "content".equals(item.getDimension())));
        assertTrue(result.getSummary().contains("审核") || result.getSuggestions().stream().anyMatch(item -> item.contains("审核")));
    }

    @Test
    void tagTopicSuggestionsReuseExistingTagAndTopicCapabilitiesWithoutCallingAi() {
        TagDTO springBoot = TagDTO.builder()
                .id(101L)
                .name("Spring Boot")
                .official(true)
                .recommended(true)
                .synonyms(List.of("Boot"))
                .build();
        TagDTO redis = TagDTO.builder()
                .id(102L)
                .name("Redis")
                .official(false)
                .recommended(true)
                .synonyms(List.of("缓存"))
                .build();
        CommunityTopicDTO topic = CommunityTopicDTO.builder()
                .id(301L)
                .slug("backend-infra")
                .name("后端基础设施")
                .virtualTopic(false)
                .tags(List.of(springBoot, redis))
                .build();
        StubAiClient aiClient = StubAiClient.disabled();
        ContentAssistService service = new ContentAssistService(
                objectMapper,
                aiClient,
                new StubTaxonomyService(List.of(springBoot, redis), List.of(topic)),
                new InMemoryRecordGateway());

        ContentAssistTagTopicSuggestionsDTO result = service.suggestTagsAndTopics(12L, ContentAssistTagTopicSuggestionsCmd.builder()
                .domain(1)
                .title("Spring Boot + Redis 链路压测复盘")
                .content("重点分析 Boot 服务和 Redis 缓存回源链路。")
                .build());

        assertEquals("rules", result.getProvider());
        assertEquals(0, aiClient.calls);
        assertEquals(2, result.getTags().size());
        assertEquals("Spring Boot", result.getTags().get(0).getName());
        assertEquals("backend-infra", result.getTopics().get(0).getSlug());
        assertTrue(result.getTags().stream().allMatch(item -> item.getReason() != null && !item.getReason().isBlank()));
    }

    @Test
    void missingDomainDoesNotPretendToBeTechDomain() {
        InMemoryRecordGateway records = new InMemoryRecordGateway();
        ContentAssistService service = new ContentAssistService(
                objectMapper,
                StubAiClient.disabled(),
                new StubTaxonomyService(List.of(), List.of()),
                records);

        ContentAssistTagTopicSuggestionsDTO result = service.suggestTagsAndTopics(18L, ContentAssistTagTopicSuggestionsCmd.builder()
                .title("cache fallback review")
                .content("redis cache fallback summary")
                .build());

        assertNull(result.getDomain());
        assertNull(result.getDomainName());
        assertNull(records.last().domain());
    }

    private static final class StubTaxonomyService implements ContentAssistTaxonomyService {
        private final List<TagDTO> tags;
        private final List<CommunityTopicDTO> topics;

        private StubTaxonomyService(List<TagDTO> tags, List<CommunityTopicDTO> topics) {
            this.tags = tags;
            this.topics = topics;
        }

        @Override
        public List<TagDTO> listTags() {
            return tags;
        }

        @Override
        public List<CommunityTopicDTO> listTopics() {
            return topics;
        }
    }

    private static final class InMemoryRecordGateway implements ContentAssistRecordGateway {
        private final List<ContentAssistAuditRecord> records = new ArrayList<>();

        @Override
        public void save(ContentAssistAuditRecord record) {
            records.add(record);
        }

        private ContentAssistAuditRecord last() {
            return records.get(records.size() - 1);
        }
    }

    private static final class StubAiClient implements ContentAssistAiClient {
        private final boolean enabled;
        private final boolean configured;
        private final String responseJson;
        private final Exception failure;
        private int calls;

        private StubAiClient(boolean enabled, boolean configured, String responseJson, Exception failure) {
            this.enabled = enabled;
            this.configured = configured;
            this.responseJson = responseJson;
            this.failure = failure;
        }

        private static StubAiClient disabled() {
            return new StubAiClient(false, false, null, null);
        }

        private static StubAiClient withResponse(String responseJson) {
            return new StubAiClient(true, true, responseJson, null);
        }

        private static StubAiClient withFailure(Exception exception) {
            return new StubAiClient(true, true, null, exception);
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public ContentAssistAiClient.Completion complete(ContentAssistScene scene, ContentAssistPrompt prompt) throws Exception {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return new ContentAssistAiClient.Completion("deepseek", responseJson, 120, 48, 360L);
        }
    }
}
