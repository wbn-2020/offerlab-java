package com.offerlab.community.notification.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPayloadGuardTest {

    @Test
    void curationFeedbackPayloadMustSurviveNotificationSanitizer() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/notification/controller/NotificationController.java");
        String listener = read("src/main/java/com/offerlab/community/notification/application/NotificationEventListener.java");

        assertContains(listener, "operationCurationSelectedContent");
        assertContains(listener, "content.put(\"eventId\"");
        assertContains(listener, "content.put(\"contentId\"");
        assertContains(listener, "content.put(\"contentTitle\"");
        assertContains(listener, "content.put(\"placementLabel\"");
        assertContains(listener, "content.put(\"sectionKey\"");
        assertContains(listener, "content.put(\"reasonText\"");

        assertContains(controller, "\"eventId\"");
        assertContains(controller, "\"contentId\"");
        assertContains(controller, "\"contentTitle\"");
        assertContains(controller, "\"placementId\"");
        assertContains(controller, "\"placementLabel\"");
        assertContains(controller, "\"sectionKey\"");
        assertContains(controller, "\"reasonText\"");
        assertContains(controller, "\"entrance\"");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
