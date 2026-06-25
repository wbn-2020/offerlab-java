package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPreferenceAliasGuardTest {

    @Test
    void notificationPreferenceAliasMustExposeNotificationScopedRoutes() throws Exception {
        Path controllerPath = Path.of("src/main/java/com/offerlab/community/user/controller/NotificationPreferenceController.java");
        assertTrue(Files.exists(controllerPath), "notification preference alias controller must exist");

        String controller = Files.readString(controllerPath, StandardCharsets.UTF_8);
        assertTrue(controller.contains("@RequestMapping(\"/api/v1/notifications/preferences\")"),
                "alias controller must use notification-scoped route");
        assertTrue(controller.contains("public Result<UserPrivacySettingDTO> getPreferences()"),
                "alias controller must expose preference query");
        assertTrue(controller.contains("public Result<UserPrivacySettingDTO> updatePreferences(@RequestBody UserPrivacySettingDTO setting)"),
                "alias controller must expose preference update");
        assertTrue(controller.contains("UserContext.require()"),
                "alias controller must resolve the authenticated user");
        assertTrue(controller.contains("userService.getPrivacySetting(uid)"),
                "alias controller must delegate reads to the existing privacy service");
        assertTrue(controller.contains("userService.updatePrivacySetting(uid, setting)"),
                "alias controller must delegate writes to the existing privacy service");
    }
}
