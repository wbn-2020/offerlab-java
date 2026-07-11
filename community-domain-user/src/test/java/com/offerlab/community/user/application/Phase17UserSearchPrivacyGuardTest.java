package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase17UserSearchPrivacyGuardTest {

    @Test
    void searchableAndProfileVisibilityMustGateAuthorDiscovery() throws Exception {
        String service = read("src/main/java/com/offerlab/community/user/application/UserApplicationService.java");
        String controller = read("src/main/java/com/offerlab/community/user/controller/UserController.java");
        String dto = read("src/main/java/com/offerlab/community/user/api/dto/UserBriefDTO.java");

        assertTrue(service.contains("privacySettingMapper.selectBatchIds(candidateIds)"),
                "Author search must batch-load privacy settings");
        assertTrue(service.contains("userFacade.batchIsFollowing(viewerUid, candidateIds)"),
                "Follower-only visibility must use one batch follow-state query");
        assertTrue(service.contains("userFacade.batchGetUserBriefs(discoverableIds)"),
                "Author search must batch-load visible user briefs");
        assertFalse(service.contains("userFacade.isSearchable(uid) && userFacade.isProfileVisible(viewerUid, uid)"),
                "Author search must not query privacy settings per candidate");
        assertTrue(service.contains(".filter(user -> !isSyntheticUser(user))"),
                "Author search must not expose synthetic fallback users as real discovery results");
        assertTrue(controller.contains("userService.searchUsers(keyword, UserContext.get(), size, userFacade)"),
                "Author search controller must pass the viewer into privacy-aware search");
        assertTrue(controller.contains("userFacade.isProfileVisible(viewer, uid)")
                        && controller.contains("dto.setPrivacyReason(\"PROFILE_RESTRICTED\")"),
                "Direct public profile reads must mask invisible profiles");
        assertTrue(controller.contains("sanitizeFollowBrief") && controller.contains("userFacade.isProfileVisible(viewer, targetUid)"),
                "Follower/following lists must not reveal private profile details through discovery edges");
        assertFalse(dto.contains("private Boolean searchable"),
                "UserBriefDTO must not disclose whether a profile is searchable");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
