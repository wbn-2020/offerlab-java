package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPreferenceContractGuardTest {

    @Test
    void notificationPreferenceEndpointMustUseNotificationScopedContract() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/user/controller/NotificationPreferenceController.java");
        String service = read("src/main/java/com/offerlab/community/user/application/UserApplicationService.java");
        String dto = read("src/main/java/com/offerlab/community/user/api/dto/NotificationPreferenceDTO.java");

        assertTrue(controller.contains("Result<NotificationPreferenceDTO> getPreferences()"),
                "notification preference query must use a notification-scoped DTO");
        assertTrue(controller.contains("Result<NotificationPreferenceDTO> updatePreferences(@RequestBody NotificationPreferenceDTO setting)"),
                "notification preference update must use a notification-scoped DTO");
        assertFalse(controller.contains("userService.getPrivacySetting(uid)"),
                "notification preference alias must not expose the full privacy read contract");
        assertFalse(controller.contains("userService.updatePrivacySetting(uid, setting)"),
                "notification preference alias must not expose the full privacy write contract");
        assertTrue(controller.contains("userService.getNotificationPreference(uid)"),
                "notification preference alias must delegate to notification-scoped reads");
        assertTrue(controller.contains("userService.updateNotificationPreference(uid, setting)"),
                "notification preference alias must delegate to notification-scoped writes");

        assertTrue(service.contains("public NotificationPreferenceDTO getNotificationPreference(Long uid)"),
                "user application service must expose notification-scoped reads");
        assertTrue(service.contains("public NotificationPreferenceDTO updateNotificationPreference(Long uid, NotificationPreferenceDTO setting)"),
                "user application service must expose notification-scoped writes");
        String notificationWriteMethod = methodBody(service, "public NotificationPreferenceDTO updateNotificationPreference(Long uid, NotificationPreferenceDTO setting)");
        assertTrue(notificationWriteMethod.contains("setInteractionNotification") && notificationWriteMethod.contains("getInteractionNotification()"),
                "notification-scoped writes must update interaction notification");
        assertTrue(notificationWriteMethod.contains("setSystemNotification") && notificationWriteMethod.contains("getSystemNotification()"),
                "notification-scoped writes must update system notification");
        assertTrue(notificationWriteMethod.contains("setLikeNotification") && notificationWriteMethod.contains("getLikeNotification()"),
                "notification-scoped writes must update like notification");
        assertFalse(notificationWriteMethod.contains("setProfileVisibility"),
                "notification-scoped writes must not touch profile visibility");
        assertFalse(notificationWriteMethod.contains("setIntentVisibility"),
                "notification-scoped writes must not touch intent visibility");
        assertFalse(notificationWriteMethod.contains("setSearchable"),
                "notification-scoped writes must not touch searchable");

        for (String field : new String[]{
                "interactionNotification",
                "systemNotification",
                "likeNotification",
                "commentNotification",
                "followNotification",
                "favoriteNotification",
                "mentionNotification"
        }) {
            assertTrue(dto.contains("private Boolean " + field), "notification preference DTO must expose " + field);
        }
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "expected method signature not found: " + signature);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "expected method body not found: " + signature);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("expected method body to close: " + signature);
    }
}
