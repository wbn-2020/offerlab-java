package com.offerlab.community.search.controller;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsModerationGuardTest {

    @Test
    void moderationUsersMustExposeUserBriefRecentViolationAndClearActions() throws Exception {
        String controllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/search/controller/OpsController.java"), StandardCharsets.UTF_8);
        String stateSource = Files.readString(Path.of("../community-infrastructure/src/main/java/com/offerlab/community/infra/moderation/UserModerationState.java"), StandardCharsets.UTF_8);
        String mapperSource = Files.readString(Path.of("../community-infrastructure/src/main/java/com/offerlab/community/infra/moderation/ContentModerationMapper.java"), StandardCharsets.UTF_8);
        String serviceSource = Files.readString(Path.of("../community-infrastructure/src/main/java/com/offerlab/community/infra/moderation/ModerationAdminService.java"), StandardCharsets.UTF_8);

        assertTrue(stateSource.contains("private String nickname"), "moderation user state must expose nickname");
        assertTrue(stateSource.contains("private String avatarUrl"), "moderation user state must expose avatar url");
        assertTrue(stateSource.contains("private String recentViolationKeyword"), "moderation user state must expose latest violation keyword");
        assertTrue(stateSource.contains("private LocalDateTime recentViolationTime"), "moderation user state must expose latest violation time");

        assertTrue(mapperSource.contains("listLatestUserViolations"), "mapper must query latest keyword hit per user");
        assertTrue(mapperSource.contains("PARTITION BY h.uid"), "latest violation query must group by user");
        assertTrue(serviceSource.contains("enrichRecentViolations(states)"), "list API must enrich recent violations");
        assertTrue(serviceSource.contains("clearUserMute"), "service must expose clear mute action");
        assertTrue(serviceSource.contains("clearUserBan"), "service must expose clear ban action");
        assertTrue(serviceSource.contains("mapper.clearMute(uid") && serviceSource.contains("RESOURCE_NOT_FOUND"), "clear mute must fail when state does not exist");
        assertTrue(serviceSource.contains("mapper.clearBan(uid") && serviceSource.contains("RESOURCE_NOT_FOUND"), "clear ban must fail when state does not exist");

        assertTrue(controllerSource.contains("UserFacade userFacade"), "ops controller must enrich user brief in user-domain boundary");
        assertTrue(controllerSource.contains("batchGetUserBriefs"), "ops controller must batch-load user brief data");
        assertTrue(controllerSource.contains("setNickname"), "ops controller must fill nickname");
        assertTrue(controllerSource.contains("setAvatarUrl"), "ops controller must fill avatar url");
        assertTrue(controllerSource.contains("/moderation/users/{targetUid}/clear-mute"), "ops controller must expose clear mute endpoint");
        assertTrue(controllerSource.contains("/moderation/users/{targetUid}/clear-ban"), "ops controller must expose clear ban endpoint");
        assertTrue(controllerSource.contains("USER_MODERATION_CLEAR_MUTE"), "clear mute must be audited");
        assertTrue(controllerSource.contains("USER_MODERATION_CLEAR_BAN"), "clear ban must be audited");
    }
}
