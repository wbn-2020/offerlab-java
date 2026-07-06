package com.offerlab.community.notification.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CurationFeedbackNotificationGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void creatorCurationFeedbackUsesSystemNotificationWithExplicitDedupBoundary() throws Exception {
        String event = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/api/event/OperationCurationSelectedEvent.java"));
        assertContains(event, "private Long authorUid");
        assertContains(event, "private Long contentId");
        assertContains(event, "private String placementType");
        assertContains(event, "private Long placementId");
        assertContains(event, "private String placementKey");
        assertContains(event, "private String sectionKey");
        assertContains(event, "private String eventType");
        assertContains(event, "OPERATION_CURATION_SELECTED");

        String operationCuration = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/OperationCurationService.java"));
        assertContains(operationCuration, "publishCreatorCurationSelectedEvents");
        assertContains(operationCuration, "isRealRemotePlacement");
        assertContains(operationCuration, "PublicContentFilter.isDistributablePost(post)");
        assertContains(operationCuration, "!Boolean.TRUE.equals(post.getAnonymous())");
        assertContains(operationCuration, "safeAuthorVisiblePost");
        assertContains(operationCuration, "STATUS_PUBLISHED");
        assertContains(operationCuration, "ITEM_ACTIVE");

        String listener = read(ROOT.resolve("community-domain-notification/src/main/java/com/offerlab/community/notification/application/NotificationEventListener.java"));
        assertContains(listener, "onOperationCurationSelected(OperationCurationSelectedEvent event)");
        assertContains(listener, "notificationFacade.notifySystem");
        assertContains(listener, "creator_curation_feedback");
        assertContains(listener, "\"eventType\"");
        assertContains(listener, "OPERATION_CURATION_SELECTED");
        assertContains(listener, "operationCurationSkippedReason");
        assertContains(listener, "NOT_PUBLISHED");
        assertContains(listener, "event.getAuthorUid()");
        assertContains(listener, "event.getContentId()");
        assertContains(listener, "\"placementType\"");
        assertContains(listener, "\"placementId\"");
        assertContains(listener, "\"placementKey\"");
        assertContains(listener, "\"placementLabel\"");
        assertContains(listener, "\"topicSlug\"");
        assertContains(listener, "\"sectionKey\"");
        assertContains(listener, "\"reasonText\"");
        assertContains(listener, "\"href\"");
        assertContains(listener, "\"source\"");
        assertContains(listener, "\"dedupKey\"");
        assertContains(listener, "\"notificationCategory\"");
        assertContains(listener, "\"system\"");

        String facade = read(ROOT.resolve("community-domain-notification/src/main/java/com/offerlab/community/notification/application/NotificationFacadeImpl.java"));
        assertContains(facade, "allowsSystemNotification(receiverUid)");
        assertContains(facade, "NotificationDedupKey.of");

        String dedup = read(ROOT.resolve("community-domain-notification/src/main/java/com/offerlab/community/notification/application/NotificationDedupKey.java"));
        assertContains(dedup, "content.get(\"dedupKey\")");
        assertContains(dedup, "String.valueOf(receiverUid)");
        assertContains(dedup, "String.valueOf(targetId)");
    }

    @Test
    void explicitOperationCurationDedupKeyParticipatesInNotificationMessageDedupKey() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", "creator_curation_feedback");
        content.put("dedupKey", "operation_curation_selected:18:1001:SLOT:2001:HOME_FEATURED:null:OPERATION_CURATION_SELECTED");

        String dedupKey = NotificationDedupKey.of(18L, 0L, 5, 1, 1001L, content);

        assertTrue(dedupKey.contains("18"));
        assertTrue(dedupKey.contains("1001"));
        assertTrue(dedupKey.contains("operation_curation_selected:18:1001:SLOT:2001:HOME_FEATURED:null:OPERATION_CURATION_SELECTED"));
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }
}
