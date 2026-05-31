package com.offerlab.community.user.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowConsistencyGuardTest {

    @Test
    void followCountersMustFollowActualRelationTransitions() throws Exception {
        String repository = read("src/main/java/com/offerlab/community/user/infrastructure/persistence/FollowRepositoryImpl.java");
        String followMapper = read("src/main/java/com/offerlab/community/user/infrastructure/persistence/mapper/UserFollowMapper.java");
        String counterMapper = read("src/main/java/com/offerlab/community/user/infrastructure/persistence/mapper/UserCounterMapper.java");
        String initSql = read("../db/init/01_user.sql");
        String migration = read("../db/migration/20260530_relation_unique_keys.sql");

        assertTrue(repository.contains("followMapper.selectAnyByPair(fromUid, toUid)"), "follow must inspect active/deleted relation rows consistently");
        assertTrue(repository.contains("followMapper.restoreById(existing.getId()) <= 0"), "follow restore must check affected rows");
        assertTrue(repository.contains("catch (DuplicateKeyException e)"), "follow unique-key races must be idempotent");
        assertTrue(repository.contains("followMapper.softDeleteById(existing.getId()) <= 0"), "unfollow must check affected rows");
        assertFalse(repository.contains("existing.setIsDeleted(1)"), "unfollow must not update stale entity state without checking affected rows");

        assertTrue(followMapper.contains("selectAnyByPair"), "mapper must expose pair lookup including deleted rows");
        assertTrue(followMapper.contains("SET is_deleted = 0 WHERE id = #{id} AND is_deleted = 1"), "restore must be state-conditional");
        assertTrue(followMapper.contains("SET is_deleted = 1 WHERE id = #{id} AND is_deleted = 0"), "soft delete must be state-conditional");
        assertTrue(counterMapper.contains("GREATEST(0, follower_count + #{delta})"), "follower count must not go negative");
        assertTrue(counterMapper.contains("GREATEST(0, following_count + #{delta})"), "following count must not go negative");
        assertTrue(initSql.contains("UNIQUE KEY uk_from_to (from_uid, to_uid)"), "fresh follow schema must use one row per pair");
        assertTrue(migration.contains("duplicate t_user_follow rows must be reviewed"), "migration must stop if historical follow duplicates exist");
    }

    @Test
    void followPagesMustUseRelationCursorAndUserBriefCacheMustEvictOnMutations() throws Exception {
        String repository = read("src/main/java/com/offerlab/community/user/infrastructure/persistence/FollowRepositoryImpl.java");
        String repositoryApi = read("src/main/java/com/offerlab/community/user/domain/repository/FollowRepository.java");
        String facadeApi = read("src/main/java/com/offerlab/community/user/api/UserFacade.java");
        String controller = read("src/main/java/com/offerlab/community/user/controller/UserController.java");
        String service = read("src/main/java/com/offerlab/community/user/application/UserApplicationService.java");
        String cacheService = read("src/main/java/com/offerlab/community/user/application/UserCacheService.java");

        assertTrue(repositoryApi.contains("List<FollowCursorDTO> followingPage"), "repository must expose relation cursor rows for following page");
        assertTrue(repositoryApi.contains("List<FollowCursorDTO> followerPage"), "repository must expose relation cursor rows for follower page");
        assertTrue(facadeApi.contains("getFollowerPage"), "facade must expose follower page rows including relation id");
        assertTrue(facadeApi.contains("getFollowingPage"), "facade must expose following page rows including relation id");
        assertTrue(repository.contains("relationId(po.getId())"), "follow page DTO must carry relation table id");
        assertTrue(repository.contains("pageLimit(size)"), "follow repository must clamp LIMIT values before SQL suffixes");
        assertTrue(controller.contains("toFollowPage(userFacade.getFollowerPage(uid, cursor, limit + 1), limit)"), "followers API must over-fetch and use relation cursor rows");
        assertTrue(controller.contains("toFollowPage(userFacade.getFollowingPage(uid, cursor, limit + 1), limit)"), "following API must over-fetch and use relation cursor rows");
        assertTrue(controller.contains("getRelationId()"), "nextCursor must come from follow relation id, not user id");

        assertTrue(cacheService.contains("CacheKeyBuilder.userProfile(uid)"), "user cache service must centralize user profile key eviction");
        assertTrue(service.contains("userCacheService.evictBrief(fromUid, toUid)"), "follow/unfollow must evict both users' brief caches");
        assertTrue(service.contains("userCacheService.evictBrief(uid)"), "profile and intent updates must evict the current user's brief cache");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
