package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSubscriptionPreferenceGuardTest {

    @Test
    void preferenceContractMustBeUidScopedValidatedAndRateLimited() throws Exception {
        String controller = read(
                "src/main/java/com/offerlab/community/user/controller/UserSubscriptionPreferenceController.java");
        String service = read(
                "src/main/java/com/offerlab/community/user/application/UserSubscriptionPreferenceService.java");
        String mapper = read(
                "src/main/java/com/offerlab/community/user/infrastructure/persistence/mapper/"
                        + "UserSubscriptionPreferenceMapper.java");
        String updateCmd = read(
                "src/main/java/com/offerlab/community/user/api/dto/"
                        + "UserSubscriptionPreferenceUpdateCmd.java");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/users/me/relationships\")"));
        assertTrue(controller.contains("@GetMapping(\"/{sourceType}/{sourceId}/preference\")"));
        assertTrue(controller.contains("@PutMapping(\"/{sourceType}/{sourceId}/preference\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/{sourceType}/{sourceId}/preference\")"));
        assertTrue(controller.contains("UserContext.require()"));
        assertTrue(count(controller, "@RateLimit") == 3);
        assertTrue(controller.contains("@Valid @RequestBody"));
        assertTrue(updateCmd.contains("@NotBlank"));
        assertTrue(updateCmd.contains("OffsetDateTime expiresAt"));

        assertTrue(service.contains("USER\", \"TOPIC\", \"DISCUSSION\", \"NEED\", \"SERIES"));
        assertFalse(service.contains("\"ACTIVITY\""));
        assertTrue(service.contains("DEFAULT_DELIVERY_MODE = \"IMMEDIATE\""));
        assertTrue(service.contains("DEFAULT_DELIVERY_MODE, \"DIGEST\", \"MUTED"));
        assertTrue(service.contains("requireActiveRelationship"));
        assertTrue(service.contains("followMapper.existsActiveFollowing"));
        assertTrue(service.contains("sourceVerifiers"));
        assertTrue(service.contains("expiresAt must be in the future"));

        assertTrue(mapper.contains("uid = #{uid}"));
        assertTrue(mapper.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(mapper.contains("is_deleted = 0"));
        assertTrue(mapper.contains("is_deleted = 1"));
        assertTrue(mapper.contains("expires_at IS NULL OR expires_at > #{now}"));
        assertTrue(mapper.contains("findEffectiveBatch"));
        assertTrue(mapper.contains("<foreach collection=\"sourceKeys\""));
        assertTrue(service.contains("MAX_BATCH_SIZE"));
        assertTrue(service.contains("preferenceMapper.findEffectiveBatch"));
    }

    @Test
    void preferenceSchemaMustKeepOneSoftDeletableFactPerUserAndSource() throws Exception {
        String migration = read("../db/migration/20260719_user_subscription_preference.sql");
        String init = read("../db/init/23_user_subscription_preference.sql");

        for (String schema : new String[]{migration, init}) {
            assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS t_user_subscription_preference"));
            assertTrue(schema.contains("PRIMARY KEY (id)"));
            assertTrue(schema.contains(
                    "UNIQUE KEY uk_user_subscription_preference_source (uid, source_type, source_id)"));
            assertTrue(schema.contains("idx_user_subscription_preference_mode"));
            assertTrue(schema.contains("delivery_mode"));
            assertTrue(schema.contains("expires_at"));
            assertTrue(schema.contains("is_deleted"));
            assertTrue(schema.contains("chk_user_subscription_preference_source_type"));
            assertTrue(schema.contains("chk_user_subscription_preference_delivery_mode"));
            assertTrue(schema.contains("chk_user_subscription_preference_deleted"));
        }
    }

    private static int count(String text, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
