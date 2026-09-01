package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.post.api.dto.DiscoveryMapDTO;
import com.offerlab.community.post.api.dto.OperationSlotDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryMapServiceTest {

    @Test
    void communityMapSeparatesFormalChannelsFromContentForms() throws Exception {
        OperationCurationService operationCurationService = mock(OperationCurationService.class);
        CommunityTopicService communityTopicService = mock(CommunityTopicService.class);
        when(operationCurationService.getPublicSlot(anyString(), anyInt()))
                .thenReturn(OperationSlotDTO.builder().items(List.of()).build());
        when(communityTopicService.listPublic(null, 8)).thenReturn(List.of());
        DiscoveryMapService service = new DiscoveryMapService(operationCurationService, communityTopicService);

        DiscoveryMapDTO map = service.getPublicMap(5, 8);

        assertEquals(List.of(
                        "channel:tech-digital",
                        "channel:career-experience",
                        "channel:learning-growth",
                        "channel:lifestyle",
                        "channel:investment"
                ),
                map.getChannels().stream().map(DiscoveryMapDTO.DiscoveryItemDTO::getId).toList());
        assertFalse(map.getChannels().stream().anyMatch(item ->
                        "channel:resources".equals(item.getId()) || "channel:qa-discussion".equals(item.getId())));

        JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(map);
        assertNotNull(json.get("contentForms"));
        assertEquals(List.of(
                        "content-form:resource",
                        "content-form:question",
                        "content-form:discussion",
                        "content-form:retrospective",
                        "content-form:checklist"
                ),
                json.get("contentForms").findValuesAsText("id"));
        assertEquals(List.of(14, 13, 16, 11, 10),
                json.get("contentForms").findValues("postType").stream()
                        .map(JsonNode::asInt)
                        .toList());
    }

    @Test
    void communityMapJsonKeepsContentFormsOutOfChannels() throws Exception {
        OperationCurationService operationCurationService = mock(OperationCurationService.class);
        CommunityTopicService communityTopicService = mock(CommunityTopicService.class);
        when(operationCurationService.getPublicSlot(anyString(), anyInt()))
                .thenReturn(OperationSlotDTO.builder().items(List.of()).build());
        when(communityTopicService.listPublic(null, 8)).thenReturn(List.of());
        DiscoveryMapService service = new DiscoveryMapService(operationCurationService, communityTopicService);

        JsonNode json = new ObjectMapper().findAndRegisterModules()
                .readTree(new ObjectMapper().findAndRegisterModules()
                        .writeValueAsString(service.getPublicMap(5, 8)));

        assertEquals(5, json.get("channels").size());
        assertEquals(5, json.get("contentForms").size());
        assertFalse(json.get("channels").findValuesAsText("id").contains("content-form:resource"));
        assertFalse(json.get("channels").findValuesAsText("id").contains("channel:resources"));
    }

    @Test
    void successfulEmptyModulesRemainHealthyEmptyStates() {
        OperationCurationService operationCurationService = mock(OperationCurationService.class);
        CommunityTopicService communityTopicService = mock(CommunityTopicService.class);
        when(operationCurationService.getPublicSlot(anyString(), anyInt()))
                .thenReturn(OperationSlotDTO.builder().items(List.of()).build());
        when(communityTopicService.listPublic(null, 8)).thenReturn(List.of());
        DiscoveryMapService service = new DiscoveryMapService(operationCurationService, communityTopicService);

        DiscoveryMapDTO map = service.getPublicMap(5, 8);

        DiscoveryMapDTO.DiscoveryModuleDTO featured = map.getModules().get("featuredTopics");
        assertEquals("EMPTY", featured.getStatus());
        assertEquals(DiscoveryMapService.SOURCE_OPERATION_CURATION, featured.getSource());
        assertFalse(featured.getDegraded());
        assertNull(featured.getFallbackReason());

        DiscoveryMapDTO.DiscoveryModuleDTO activeTopics = map.getModules().get("activeTopics");
        assertEquals("EMPTY", activeTopics.getStatus());
        assertEquals(DiscoveryMapService.SOURCE_COMMUNITY_TOPIC, activeTopics.getSource());
        assertFalse(activeTopics.getDegraded());
        assertNull(activeTopics.getFallbackReason());
        assertFalse(map.getDegraded());
    }

    @Test
    void failedModulesAreUnavailableWithoutEscalatingHealthyModules() {
        OperationCurationService operationCurationService = mock(OperationCurationService.class);
        CommunityTopicService communityTopicService = mock(CommunityTopicService.class);
        when(operationCurationService.getPublicSlot(anyString(), anyInt()))
                .thenThrow(new BizException(ErrorCode.SYSTEM_ERROR));
        when(communityTopicService.listPublic(null, 8))
                .thenThrow(new BizException(ErrorCode.SYSTEM_ERROR));
        DiscoveryMapService service = new DiscoveryMapService(operationCurationService, communityTopicService);

        DiscoveryMapDTO map = service.getPublicMap(5, 8);

        DiscoveryMapDTO.DiscoveryModuleDTO featured = map.getModules().get("featuredTopics");
        assertEquals("UNAVAILABLE", featured.getStatus());
        assertEquals("unavailable", featured.getSource());
        assertTrue(featured.getDegraded());
        assertEquals("discovery_featured_topics_unavailable", featured.getFallbackReason());

        DiscoveryMapDTO.DiscoveryModuleDTO activeTopics = map.getModules().get("activeTopics");
        assertEquals("UNAVAILABLE", activeTopics.getStatus());
        assertTrue(activeTopics.getDegraded());
        assertEquals("community_topics_unavailable", activeTopics.getFallbackReason());

        assertEquals("READY", map.getModules().get("channels").getStatus());
        assertFalse(map.getModules().get("channels").getDegraded());
        assertTrue(map.getDegraded());
    }

    private static String href(DiscoveryMapDTO map, String id) {
        return map.getChannels().stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow()
                .getHref();
    }
}
