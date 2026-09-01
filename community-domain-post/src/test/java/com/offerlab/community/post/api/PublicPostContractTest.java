package com.offerlab.community.post.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicPostContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void publicExtensionKeepsProductFieldsAndDropsGovernanceFields() throws Exception {
        String sanitized = PublicPostExtensionSanitizer.sanitize("""
                {
                  "domain": 1,
                  "company": "OfferLab",
                  "techStacks": ["Java", "Redis"],
                  "featured": true,
                  "featuredBy": 9911,
                  "featuredNote": "internal note",
                  "knowledgeReviewedBy": 9922,
                  "knowledgeReviewNote": "private review"
                }
                """);

        JsonNode value = JSON.readTree(sanitized);
        assertEquals(1, value.path("domain").asInt());
        assertEquals("OfferLab", value.path("company").asText());
        assertTrue(value.path("featured").asBoolean());
        assertFalse(value.has("featuredBy"));
        assertFalse(value.has("featuredNote"));
        assertFalse(value.has("knowledgeReviewedBy"));
        assertFalse(value.has("knowledgeReviewNote"));
    }

    @Test
    void publicExtensionFailsClosedForMalformedOrInternalOnlyPayloads() {
        assertNull(PublicPostExtensionSanitizer.sanitize("{not-json"));
        assertNull(PublicPostExtensionSanitizer.sanitize("""
                {"featuredBy":9911,"knowledgeReviewNote":"private review"}
                """));
    }

    @Test
    void publicDtoSerializationOmitsInternalIdentityAndGovernanceState() throws Exception {
        PostDTO detail = PostDTO.builder()
                .id(10L)
                .authorId(20L)
                .visibility(1)
                .postStatus(20)
                .contentEnvironment("COMMUNITY")
                .title("公开标题")
                .build();
        PostBriefDTO brief = PostBriefDTO.builder()
                .id(11L)
                .authorId(21L)
                .contentEnvironment("COMMUNITY")
                .title("公开摘要")
                .build();

        JsonNode detailJson = JSON.readTree(JSON.writeValueAsString(detail));
        JsonNode briefJson = JSON.readTree(JSON.writeValueAsString(brief));

        assertFalse(detailJson.has("authorId"));
        assertFalse(detailJson.has("visibility"));
        assertFalse(detailJson.has("postStatus"));
        assertFalse(detailJson.has("contentEnvironment"));
        assertFalse(briefJson.has("authorId"));
        assertFalse(briefJson.has("contentEnvironment"));
    }
}
