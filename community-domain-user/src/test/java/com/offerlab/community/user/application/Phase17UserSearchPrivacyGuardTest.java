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
        assertTrue(service.contains("!adminPermissionService.isAdmin(uid)"),
                "Author discovery must exclude actual administrator accounts");
        assertTrue(service.contains("publicContentMapper.countPublicPostsByAuthors(authorIds)"),
                "Author discovery must use the shared public-content boundary for post counts");
        assertTrue(service.contains("user.setPostCount(publicPostCounts.getOrDefault(user.getUid(), 0L))"),
                "Author search DTOs must expose the real public post count");
        String publicContentMapper = read(
                "src/main/java/com/offerlab/community/user/infrastructure/persistence/mapper/UserPublicContentMapper.java");
        assertTrue(publicContentMapper.contains("p.content_environment = 'COMMUNITY'")
                        && publicContentMapper.contains("p.post_status = 1")
                        && publicContentMapper.contains("p.visibility = 1")
                        && publicContentMapper.contains("p.is_deleted = 0"),
                "Public author counts must use the COMMUNITY published/public boundary");
        assertFalse(publicContentMapper.contains("NOT LIKE")
                        || publicContentMapper.contains("CONCAT_WS")
                        || publicContentMapper.contains("t_post_extension"),
                "Public author counts must not infer author eligibility from legitimate post text or metadata");
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
