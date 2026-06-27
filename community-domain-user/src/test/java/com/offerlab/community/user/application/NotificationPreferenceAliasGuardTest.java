package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPreferenceAliasGuardTest {

    @Test
    void notificationPreferenceAliasMustExposeNotificationScopedRoutes() throws Exception {
        Path controllerPath = Path.of("src/main/java/com/offerlab/community/user/controller/NotificationPreferenceController.java");
        Path servicePath = Path.of("src/main/java/com/offerlab/community/user/application/UserApplicationService.java");
        Path dtoPath = Path.of("src/main/java/com/offerlab/community/user/api/dto/NotificationPreferenceDTO.java");
        assertTrue(Files.exists(controllerPath), "notification preference alias controller must exist");
        assertTrue(Files.exists(servicePath), "user application service must exist");
        assertTrue(Files.exists(dtoPath), "notification preference DTO must exist");

        String controller = Files.readString(controllerPath, StandardCharsets.UTF_8);
        String service = Files.readString(servicePath, StandardCharsets.UTF_8);
        String dto = Files.readString(dtoPath, StandardCharsets.UTF_8);
        assertTrue(controller.contains("@RequestMapping(\"/api/v1/notifications/preferences\")"),
                "alias controller must use notification-scoped route");
        assertTrue(controller.contains("public Result<NotificationPreferenceDTO> getPreferences()"),
                "alias controller must expose notification-scoped preference query");
        assertTrue(controller.contains("public Result<NotificationPreferenceDTO> updatePreferences(@RequestBody NotificationPreferenceDTO setting)"),
                "alias controller must expose notification-scoped preference update");
        assertFalse(controller.contains("UserPrivacySettingDTO"),
                "alias controller must not expose the full privacy setting DTO");
        assertTrue(controller.contains("UserContext.require()"),
                "alias controller must resolve the authenticated user");
        assertTrue(controller.contains("userService.getNotificationPreference(uid)"),
                "alias controller must delegate reads to the notification preference service");
        assertTrue(controller.contains("userService.updateNotificationPreference(uid, setting)"),
                "alias controller must delegate writes to the notification preference service");
        assertFalse(controller.contains("userService.getPrivacySetting(uid)"),
                "alias controller must not delegate reads to the full privacy service");
        assertFalse(controller.contains("userService.updatePrivacySetting(uid, setting)"),
                "alias controller must not delegate writes to the full privacy service");

        assertTrue(service.contains("public NotificationPreferenceDTO getNotificationPreference(Long uid)"),
                "user application service must expose notification-scoped reads");
        assertTrue(service.contains("public NotificationPreferenceDTO updateNotificationPreference(Long uid, NotificationPreferenceDTO setting)"),
                "user application service must expose notification-scoped writes");
        assertTrue(dto.contains("public class NotificationPreferenceDTO"),
                "notification preference contract must have a dedicated DTO");
    }
}
