package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionConsistencyGuardTest {

    @Test
    void interactionCountersMustFollowActualStateTransitions() throws Exception {
        String service = read("src/main/java/com/offerlab/community/interaction/application/InteractionFacadeImpl.java");
        String likeMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/LikeMapper.java");
        String favoriteMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/FavoriteMapper.java");
        String commentMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/CommentMapper.java");
        String counterMapper = read("../community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostCounterMapper.java");
        String initSql = read("../db/init/03_interaction.sql");
        String migration = read("../db/migration/20260530_relation_unique_keys.sql");

        assertTrue(service.contains("postFacade.getPost(postId, uid)"), "like/favorite must validate visibility for the caller");
        assertTrue(service.contains("postFacade.getPost(cmd.getPostId(), cmd.getAuthorUid())"), "comments must validate visibility for the author");
        assertTrue(service.contains("catch (DuplicateKeyException e)"), "unique-key races must map to business errors");
        assertTrue(service.contains("likeMapper.restoreById(existing.getId()) <= 0"), "like restore must check affected rows");
        assertTrue(service.contains("favoriteMapper.restoreById(existing.getId()) <= 0"), "favorite restore must check affected rows");
        assertTrue(service.contains("likeMapper.softDeleteById(po.getId()) <= 0"), "unlike must check affected rows");
        assertTrue(service.contains("favoriteMapper.softDeleteById(po.getId()) <= 0"), "unfavorite must check affected rows");
        assertTrue(service.contains("int deletedCount = commentMapper.delete(deleteQuery)"), "comment deletion must use real affected rows");
        assertFalse(service.contains("Long deletedCount = commentMapper.selectCount(deleteQuery)"), "comment deletion must not count then delete");

        assertTrue(likeMapper.contains("AND is_deleted = 1"), "like restore must only restore deleted rows");
        assertTrue(favoriteMapper.contains("AND is_deleted = 1"), "favorite restore must only restore deleted rows");
        assertTrue(commentMapper.contains("GREATEST(0, like_count + #{delta})"), "comment like count must be atomic and non-negative");
        assertTrue(counterMapper.contains("GREATEST(0, like_count + #{delta})"), "post like count must not go negative");
        assertTrue(counterMapper.contains("GREATEST(0, comment_count + #{delta})"), "post comment count must not go negative");
        assertTrue(counterMapper.contains("GREATEST(0, favorite_count + #{delta})"), "post favorite count must not go negative");

        assertTrue(initSql.contains("UNIQUE KEY uk_user_target (user_id, target_type, target_id)"), "fresh like schema must use one relation row per target");
        assertTrue(initSql.contains("UNIQUE KEY uk_user_post (user_id, post_id)"), "fresh favorite schema must use one relation row per post");
        assertTrue(migration.contains("v20260530_rel_assert_no_duplicates"), "migration must block unsafe duplicate relation data");
        assertFalse(migration.toLowerCase().contains("delete "), "relation migration must not auto-delete historical rows");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
