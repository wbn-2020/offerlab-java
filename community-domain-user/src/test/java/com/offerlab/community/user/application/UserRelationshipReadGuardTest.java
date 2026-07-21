package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRelationshipReadGuardTest {

    @Test
    void userRelationshipFacadeMustUseActiveUidScopedAndPrivacyFilteredSql() throws Exception {
        String facade = read("src/main/java/com/offerlab/community/user/api/UserRelationshipReadFacade.java");
        String service = read("src/main/java/com/offerlab/community/user/application/UserRelationshipReadService.java");
        String mapper = read("src/main/java/com/offerlab/community/user/infrastructure/persistence/mapper/UserFollowMapper.java");

        assertTrue(facade.contains("listFollowing"));
        assertTrue(facade.contains("countFollowing"));
        assertTrue(service.contains("selectFollowingRelationshipRows"));
        assertTrue(service.contains("countVisibleFollowingRelationships"));
        assertTrue(mapper.contains("f.from_uid = #{fromUid}"));
        assertTrue(mapper.contains("f.is_deleted = 0"));
        assertTrue(mapper.contains("p.is_deleted = 0"));
        assertTrue(mapper.contains("ps.user_id = p.id"));
        assertTrue(mapper.contains("profile_visibility"));
        assertTrue(mapper.contains("countVisibleFollowingRelationships"));
        assertTrue(mapper.contains("f.create_time &lt; #{cursorTime}"));
        assertTrue(mapper.contains("f.id &lt; #{cursorId}"));
        assertTrue(mapper.contains("ORDER BY f.create_time DESC, f.id DESC"));
        assertTrue(mapper.contains("t_user_subscription_preference"));
        assertTrue(mapper.contains("pref.uid = f.from_uid"));
        assertTrue(mapper.contains("COALESCE(pref.delivery_mode, 'IMMEDIATE')"));
        assertTrue(mapper.contains("mode == 'ACTIVE'"));
        assertTrue(mapper.contains("mode == 'MUTED'"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
