package com.offerlab.community.interaction.application;

import com.offerlab.community.interaction.api.enums.ContentSuggestionDecision;
import com.offerlab.community.interaction.api.enums.ContentSuggestionResolution;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSuggestionV10CompatibilityTest {

    @Test
    void legacyDecisionsMapToCanonicalResolution() {
        assertEquals(
                ContentSuggestionResolution.ACCEPTED,
                ContentSuggestionResolution.fromDecision(ContentSuggestionDecision.ACCEPTED));
        assertEquals(
                ContentSuggestionResolution.PARTIAL,
                ContentSuggestionResolution.fromDecision(ContentSuggestionDecision.PARTIAL_ACCEPTED));
        assertEquals(
                ContentSuggestionResolution.REJECTED,
                ContentSuggestionResolution.fromDecision(ContentSuggestionDecision.REJECTED));
        assertEquals(
                ContentSuggestionResolution.ACCEPTED,
                ContentSuggestionResolution.fromDecision(ContentSuggestionDecision.MERGED));
        assertEquals(
                ContentSuggestionResolution.PLANNED,
                ContentSuggestionResolution.fromDecision(ContentSuggestionDecision.PLANNED));
        assertEquals(
                ContentSuggestionResolution.PENDING,
                ContentSuggestionResolution.fromDecision(null));

        assertEquals(
                ContentSuggestionDecision.PARTIAL_ACCEPTED,
                ContentSuggestionResolution.PARTIAL.toCompatibleDecision());
        assertNull(ContentSuggestionResolution.PENDING.toCompatibleDecision());
    }

    @Test
    void onlyPositiveResolvedStatesCanLinkToVersion() {
        assertTrue(ContentSuggestionResolution.ACCEPTED.canLinkToVersion());
        assertTrue(ContentSuggestionResolution.PARTIAL.canLinkToVersion());
        assertTrue(ContentSuggestionResolution.PLANNED.canLinkToVersion());
        assertFalse(ContentSuggestionResolution.PENDING.canLinkToVersion());
        assertFalse(ContentSuggestionResolution.REJECTED.canLinkToVersion());
    }

    @Test
    void v10PersistenceAndListenerUseCanonicalStateWithLegacyFallback() throws Exception {
        String dto = read("src/main/java/com/offerlab/community/interaction/api/dto/ContentSuggestionDTO.java");
        String submitCmd = read("src/main/java/com/offerlab/community/interaction/api/dto/ContentSuggestionSubmitCmd.java");
        String decisionCmd = read("src/main/java/com/offerlab/community/interaction/api/dto/ContentSuggestionDecisionCmd.java");
        String po = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/po/ContentSuggestionPO.java");
        String mapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/ContentSuggestionMapper.java");
        String service = read("src/main/java/com/offerlab/community/interaction/application/TrustedContentService.java");
        String listener = read("src/main/java/com/offerlab/community/interaction/application/TrustedContentEventListener.java");

        for (String field : new String[] {
                "baseVersion", "targetScope", "targetLocator", "expectedChange",
                "resolution", "deliveryStatus"
        }) {
            assertTrue(dto.contains(field), "content suggestion DTO must expose " + field);
            assertTrue(po.contains(field), "content suggestion persistence must include " + field);
        }
        for (String field : new String[] {"targetScope", "targetLocator", "expectedChange"}) {
            assertTrue(submitCmd.contains(field), "submission command must accept " + field);
        }
        assertTrue(decisionCmd.contains("ContentSuggestionDecision decision"),
                "legacy decision input must remain available");
        assertTrue(decisionCmd.contains("ContentSuggestionResolution resolution"),
                "canonical resolution input must be available");

        for (String column : new String[] {
                "base_version", "target_scope", "target_locator", "expected_change",
                "resolution", "delivery_status"
        }) {
            assertTrue(mapper.contains(column), "content suggestion SQL must include " + column);
        }
        assertTrue(mapper.contains("resolution = 'PENDING'"),
                "pending reads and writes must use canonical resolution state");
        assertFalse(mapper.contains("decision IS NULL"),
                "V10 lifecycle SQL must not use legacy null decisions as the state authority");
        assertFalse(mapper.contains("decision IS NOT NULL"),
                "V10 lifecycle SQL must not use legacy decisions as the decided-state authority");
        assertTrue(mapper.contains("delivery_status = 'UNLINKED'"));
        assertTrue(mapper.contains("delivery_status = 'LINKED'"));
        assertTrue(mapper.contains("result_version IS NULL"),
                "version response must only link a suggestion once");
        assertTrue(mapper.contains("#{resolution} IN ('ACCEPTED', 'PARTIAL', 'PLANNED')"),
                "only positive resolved states may be linked");

        assertTrue(service.contains("suggestion.setBaseVersion(currentPost.getVersion())"),
                "submission must capture the current post version");
        assertTrue(service.contains("expectedChange == null ? detail : expectedChange"),
                "legacy detail-only submissions must retain an expected change");
        assertTrue(service.contains("ContentSuggestionResolution.fromDecision(legacyDecision)"),
                "old decision values must remain readable after V10 defaults are applied");
        assertTrue(service.contains("suggestion.getResultVersion() != null"),
                "old linked rows must remain readable when delivery status has the V10 default");
        assertTrue(service.contains("resolution == ContentSuggestionResolution.REJECTED"),
                "rejected suggestions must never link to a version");
        assertTrue(service.contains("resolution == ContentSuggestionResolution.PENDING"),
                "a legacy pending version response must first resolve the suggestion as accepted");
        assertTrue(listener.contains("linkSuggestionsFromPostUpdate"),
                "post update responses must use the version-linking lifecycle");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
