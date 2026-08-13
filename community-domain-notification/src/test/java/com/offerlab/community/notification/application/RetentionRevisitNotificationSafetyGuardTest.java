package com.offerlab.community.notification.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionRevisitNotificationSafetyGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void retentionRevisitMustReuseInSiteNotificationPreferencesAndTrustedSources() throws Exception {
        String facade = read(ROOT.resolve("community-domain-notification/src/main/java/com/offerlab/community/notification/application/NotificationFacadeImpl.java"));
        String listener = read(ROOT.resolve("community-domain-notification/src/main/java/com/offerlab/community/notification/application/NotificationEventListener.java"));
        String questionListener = read(ROOT.resolve("community-domain-notification/src/main/java/com/offerlab/community/notification/application/QuestionNotificationListener.java"));
        String preferenceDto = read(ROOT.resolve("community-domain-user/src/main/java/com/offerlab/community/user/api/dto/NotificationPreferenceDTO.java"));
        String privacySql = read(ROOT.resolve("db/init/08_privacy.sql"));

        assertContains(facade, "allowsLikeNotification");
        assertContains(facade, "allowsCommentNotification");
        assertContains(facade, "allowsFollowNotification");
        assertContains(facade, "allowsFavoriteNotification");
        assertContains(facade, "allowsMentionNotification");
        assertContains(facade, "allowsSystemNotification");
        assertContains(listener, "subscriptionUpdateDeliveryService");

        for (String action : List.of(
                "\"like\"",
                "\"comment\"",
                "\"follow\"",
                "\"favorite\"",
                "\"mention\"",
                "\"topic_post_published\"",
                "\"question_extract_succeeded\"",
                "\"question_extract_failed\"")) {
            assertTrue(listener.contains(action) || questionListener.contains(action),
                    () -> "Notification action source must stay explicit and trusted: " + action);
        }

        assertForbiddenSourceAbsent(listener + questionListener + facade);
        assertFalse(preferenceDto.contains("retentionNotification") || preferenceDto.contains("revisitNotification"),
                "P0 must not expose a fake independent retention notification preference.");
        assertFalse(privacySql.contains("retention_notification") || privacySql.contains("revisit_notification"),
                "P0 must not add a DB preference column unless the full persistence contract is implemented.");
    }

    private static void assertForbiddenSourceAbsent(String source) {
        for (String forbidden : List.of(
                "sms",
                "email",
                "deviceToken",
                "externalPush",
                "advertising",
                "payment",
                "retentionNotification")) {
            assertFalse(source.contains(forbidden), () -> "Retention revisit must not introduce source: " + forbidden);
        }
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
