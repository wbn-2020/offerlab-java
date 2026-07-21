package com.offerlab.community.post.relationship.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipReadGuardTest {

    @Test
    void relationshipEndpointsMustRequireLoginAndRateLimit() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/relationship/controller/RelationshipController.java");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/users/me\")"));
        assertTrue(controller.contains("@GetMapping(\"/relationships\")"));
        assertTrue(controller.contains("@GetMapping(\"/relationship-summary\")"));
        assertTrue(controller.contains("UserContext.require()"));
        assertTrue(controller.contains("@RateLimit"));
        assertFalse(controller.contains("@PublicApi"));
    }

    @Test
    void aggregateQueriesMustFilterByUidAndPublicVisibilityBeforeMerging() throws Exception {
        String mapper = read("src/main/java/com/offerlab/community/post/relationship/infrastructure/persistence/RelationshipMapper.java");
        String userMapper = read("../community-domain-user/src/main/java/com/offerlab/community/user/infrastructure/persistence/mapper/UserFollowMapper.java");
        String service = read("src/main/java/com/offerlab/community/post/relationship/application/RelationshipQueryService.java");

        assertTrue(mapper.contains("f.uid = #{uid}"));
        assertTrue(mapper.contains("m.uid = #{uid}"));
        assertTrue(mapper.contains("t.is_deleted = 0"));
        assertTrue(mapper.contains("p.is_deleted = 0"));
        assertTrue(mapper.contains("n.moderation_hidden = 0"));
        assertTrue(mapper.contains("s.moderation_hidden = 0"));
        assertTrue(mapper.contains("relationTime &lt; #{cursorTime}"));
        assertTrue(mapper.contains("relationId &lt; #{cursorId}"));
        assertTrue(mapper.contains("ORDER BY relationTime DESC, relationId DESC, sourceType ASC"));
        assertTrue(userMapper.contains("f.from_uid = #{fromUid}"));
        assertTrue(userMapper.contains("p.is_deleted = 0"));
        assertTrue(userMapper.contains("profile_visibility"));
        assertTrue(service.contains("UserRelationshipReadFacade"));
        assertTrue(service.contains("DEFAULT_DELIVERY_MODE"));
        assertTrue(service.contains("normalizeSourceType"));
        assertTrue(service.contains("RelationshipCursor"));
        assertFalse(service.contains("t_user_subscription_preference"));
    }

    @Test
    void supportedRelationshipTypesMustBeExplicitAndActivitiesCannotBeInvented() throws Exception {
        String service = read("src/main/java/com/offerlab/community/post/relationship/application/RelationshipQueryService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/relationship/infrastructure/persistence/RelationshipMapper.java");

        for (String sourceType : new String[]{"USER", "TOPIC", "DISCUSSION", "NEED", "SERIES"}) {
            assertTrue(service.contains("\"" + sourceType + "\""), "missing source type: " + sourceType);
        }
        assertFalse(service.contains("\"ACTIVITY\""));
        assertFalse(mapper.contains("ACTIVITY AS sourceType"),
                "activity must not be exposed without an existing active relationship fact");
        assertTrue(service.contains("MUTED"));
        assertTrue(mapper.contains("mode == 'MUTED'"));
        assertTrue(service.contains("pageCursor.sourceType(), normalizedMode, FETCH_LIMIT"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
