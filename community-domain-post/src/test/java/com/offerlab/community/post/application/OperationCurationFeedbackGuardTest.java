package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationCurationFeedbackGuardTest {

    @Test
    void authorVisibleFeedbackOnlyComesFromPublishedRealPublicContentAndPublishesEvents() throws Exception {
        String service = read("src/main/java/com/offerlab/community/post/application/OperationCurationService.java");
        String facade = read("src/main/java/com/offerlab/community/post/api/CreatorCurationFeedbackFacade.java");
        String dto = read("src/main/java/com/offerlab/community/post/api/dto/OperationCurationFeedbackDTO.java");

        assertTrue(facade.contains("listCreatorCurationFeedback"),
                "creator growth must read curation feedback through a small post-domain facade");
        assertTrue(dto.contains("private Long contentId")
                        && dto.contains("private String contentTitle")
                        && dto.contains("private String placementType")
                        && dto.contains("private Long placementId")
                        && dto.contains("private String placementKey")
                        && dto.contains("private String reason")
                        && dto.contains("private String entrance")
                        && dto.contains("private String status"),
                "feedback DTO must expose content, position, reason, entrance, and status");
        assertTrue(service.contains("implements CreatorCurationFeedbackFacade"),
                "OperationCurationService must provide the creator feedback read facade");
        assertTrue(service.contains("listCreatorCurationFeedback(Long authorUid, int limit)"),
                "creator feedback read API must be implemented in operation curation service");
        assertTrue(service.contains("new LinkedHashMap<>()"),
                "creator feedback de-duplication must preserve stable insertion order before explicit sorting");
        assertTrue(service.contains("Comparator.comparing(OperationCurationFeedbackDTO::getUpdateTime"),
                "creator feedback must sort by updateTime before applying limits so recentItems is deterministic");
        assertTrue(service.contains("STATUS_PUBLISHED"),
                "author feedback must be based on published operation snapshots only");
        assertTrue(service.contains("PublicContentFilter.isDistributablePost(post)")
                        && service.contains("!Boolean.TRUE.equals(post.getAnonymous())"),
                "anonymous, private, deleted, reviewing, violating, and synthetic content must not become author-visible feedback");
        assertTrue(service.contains("isRealRemotePlacement"),
                "fallback/demo/fixture placements must not create real author feedback");
        assertTrue(service.contains("applicationEventPublisher.publishEvent")
                        && service.contains("OperationCurationSelectedEvent"),
                "publishing a real placement must emit a creator curation selected event");
        assertTrue(service.contains("publishCreatorCurationSelectedEvents(current)"),
                "draft and preview edits must not send real notifications; only publish/rollback of published snapshots may emit events");
        assertTrue(service.contains("rollback") && service.contains("listCreatorCurationFeedback"),
                "offline/rollback must leave author reads tied to the current published snapshot and degrade when offline");
        assertTrue(service.contains("publicCurationReason("),
                "public creator feedback reasonText must come from a public-safe reason helper instead of raw admin notes");
        assertTrue(!service.contains(".reason(item.getNote())") && !service.contains(".reason(section.getNote())"),
                "creator feedback must not expose raw admin notes as public reason text");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
