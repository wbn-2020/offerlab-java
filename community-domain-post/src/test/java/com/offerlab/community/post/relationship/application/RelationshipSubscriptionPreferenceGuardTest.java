package com.offerlab.community.post.relationship.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipSubscriptionPreferenceGuardTest {

    @Test
    void relationshipProjectionMustApplyUidScopedEffectivePreferenceBeforePaging() throws Exception {
        String mapper = read(
                "src/main/java/com/offerlab/community/post/relationship/infrastructure/persistence/"
                        + "RelationshipMapper.java");
        String service = read(
                "src/main/java/com/offerlab/community/post/relationship/application/RelationshipQueryService.java");
        String userMapper = read(
                "../community-domain-user/src/main/java/com/offerlab/community/user/infrastructure/persistence/"
                        + "mapper/UserFollowMapper.java");

        assertTrue(mapper.contains("t_user_subscription_preference pref"));
        assertTrue(mapper.contains("pref.uid = #{uid}"));
        assertTrue(mapper.contains("pref.source_type = relationshipRows.sourceType"));
        assertTrue(mapper.contains("pref.is_deleted = 0"));
        assertTrue(mapper.contains("pref.expires_at IS NULL OR pref.expires_at > CURRENT_TIMESTAMP(3)"));
        assertTrue(mapper.contains("mode == 'ACTIVE'"));
        assertTrue(mapper.contains("mode == 'MUTED'"));
        assertTrue(mapper.contains("COALESCE(pref.delivery_mode, 'IMMEDIATE')"));
        assertTrue(mapper.contains("GROUP BY relationshipCounts.sourceType"));

        assertTrue(userMapper.contains("pref.source_type = 'USER'"));
        assertTrue(userMapper.contains("mode == 'ACTIVE'"));
        assertTrue(userMapper.contains("mode == 'MUTED'"));
        assertTrue(service.contains("countFollowingByDeliveryMode"));
        assertTrue(service.contains(".expiresAt(row.getExpiresAt())"));
        assertTrue(service.contains(".active(Math.max(0L, total - muted))"));
    }

    @Test
    void sourceVerifierMustNotAllowUnsupportedActivityPreferenceToCreateAState() throws Exception {
        String verifier = read(
                "src/main/java/com/offerlab/community/post/relationship/application/"
                        + "PostRelationshipSourceVerifier.java");
        String service = read(
                "../community-domain-user/src/main/java/com/offerlab/community/user/application/"
                        + "UserSubscriptionPreferenceService.java");

        assertTrue(verifier.contains("TOPIC\", \"DISCUSSION\", \"NEED\", \"SERIES"));
        assertTrue(!verifier.contains("\"ACTIVITY\""));
        assertTrue(service.contains("sourceVerifiers.stream()"));
        assertTrue(service.contains("ErrorCode.RESOURCE_NOT_FOUND"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
