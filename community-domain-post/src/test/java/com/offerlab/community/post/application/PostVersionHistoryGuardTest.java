package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostVersionHistoryGuardTest {

    @Test
    void postVersionHistorySchemaMustBeNonDestructiveAndIndexed() throws Exception {
        String migration = read("../db/migration/20260530_post_version_history.sql").toLowerCase();
        String init = read("../db/init/02_post.sql").toLowerCase();

        assertTrue(migration.contains("create table if not exists t_post_version_history"), "migration must create version history table safely");
        assertTrue(migration.contains("post_id"), "history rows must link to the source post");
        assertTrue(migration.contains("author_id"), "history rows must keep the post author");
        assertTrue(migration.contains("editor_uid"), "history rows must keep the editor uid");
        assertTrue(migration.contains("base_version"), "history rows must keep the optimistic-lock base version");
        assertTrue(migration.matches("(?s).*content\\s+longtext.*"), "history rows must keep the previous content snapshot");
        assertTrue(migration.contains("tag_snapshot_json"), "history rows must keep the previous tag snapshot");
        assertTrue(migration.contains("change_summary"), "history rows must expose changed fields for review");
        assertTrue(migration.contains("idx_post_time"), "history list needs a post/time index");
        assertTrue(migration.contains("idx_author_time"), "author history lookups need an index");
        assertTrue(migration.contains("idx_editor_time"), "moderation audit lookups need an editor/time index");
        assertFalse(migration.contains("drop table"), "version history migration must not drop data");

        assertTrue(init.contains("create table t_post_version_history"), "fresh local schema must include post version history");
        int draftStart = init.indexOf("create table t_post_draft");
        int versionDrop = init.indexOf("drop table if exists t_post_version_history");
        assertTrue(draftStart >= 0 && versionDrop > draftStart, "draft table must appear before version history in init SQL");
        String draftBlock = init.substring(draftStart, versionDrop).trim();
        assertTrue(draftBlock.endsWith(";"), "draft table definition must be closed before version history table starts");
        assertFalse(draftBlock.contains("create table t_post_version_history"), "version history table must not be nested inside draft SQL");
    }

    @Test
    void postUpdateMustSnapshotTheOldVersionBeforeMutation() throws Exception {
        String appService = read("src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String versionService = read("src/main/java/com/offerlab/community/post/application/PostVersionHistoryService.java");
        String repo = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/PostRepositoryImpl.java");
        String domain = read("src/main/java/com/offerlab/community/post/domain/model/Post.java");
        String mybatisConfig = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/mybatis/config/MybatisPlusConfig.java");

        assertTrue(appService.contains("private final PostVersionHistoryService versionHistoryService"), "post application service must own snapshot orchestration");
        int snapshotCall = appService.indexOf("versionHistoryService.snapshotBeforeUpdate");
        int firstMutation = appService.indexOf("post.setVisibility(nextVisibility)");
        assertTrue(snapshotCall > 0 && firstMutation > snapshotCall, "snapshot must be taken before the post object is mutated");
        assertTrue(appService.contains("tagsByIds(existingTagIds)"), "snapshot must store the existing tags before syncTags changes them");
        assertTrue(appService.contains("post.getVersion()"), "snapshot must record the base optimistic-lock version");

        assertTrue(versionService.contains("versionMapper.tableExists() <= 0"), "snapshot should fail open when old databases have not migrated yet");
        assertTrue(versionService.contains("catch (Exception e)"), "snapshot failure must not break the edit flow");
        assertTrue(versionService.contains("po.setTitle(current.getTitle())"), "snapshot must store the previous title");
        assertTrue(versionService.contains("po.setContent(current.getContent())"), "snapshot must store the previous content");
        assertTrue(versionService.contains("po.setTagSnapshotJson(writeTags(currentTags))"), "snapshot must store previous tags");
        assertTrue(versionService.contains("if (\"no-op\".equals(changeSummary))"), "no-op edits should not create noisy history rows");
        assertTrue(versionService.contains("Math.max(1, Math.min"), "history list limit must be bounded");

        assertTrue(domain.contains("private Integer version"), "post domain must carry the DB version for snapshots");
        assertTrue(repo.contains(".version(po.getVersion())"), "repository must map the DB version into the domain object");
        assertTrue(repo.contains("po.setVersion(post.getVersion())"), "post updates must pass the base version to MyBatis-Plus optimistic locking");
        assertFalse(repo.contains("post.getVersion() + 1"), "repository must not manually advance @Version before updateById");
        assertFalse(repo.contains("po.setVersion(null)"), "post updates must not clear the version field before saving");
        assertTrue(mybatisConfig.contains("MybatisPlusInterceptor"), "MyBatis-Plus interceptor must be registered for @Version fields");
        assertTrue(mybatisConfig.contains("OptimisticLockerInnerInterceptor"), "post optimistic locking must install the version interceptor");
    }

    @Test
    void versionHistoryApiMustBeAuthorOrModeratorOnly() throws Exception {
        String api = read("src/main/java/com/offerlab/community/post/api/PostFacade.java");
        String facade = read("src/main/java/com/offerlab/community/post/application/PostFacadeImpl.java");
        String controller = read("src/main/java/com/offerlab/community/post/controller/PostController.java");
        String dto = read("src/main/java/com/offerlab/community/post/api/dto/PostVersionHistoryDTO.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostVersionHistoryMapper.java");
        String po = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/po/PostVersionHistoryPO.java");

        assertTrue(api.contains("listPostVersions"), "facade API must expose a version history contract");
        assertTrue(controller.contains("@GetMapping(\"/{postId}/versions\")"), "post controller must expose the stable versions endpoint");
        assertTrue(controller.contains("UserContext.require()"), "version history must require a logged-in viewer");
        assertTrue(controller.contains("adminPermissionService.isAdmin(uid)"), "admins must be able to inspect history");
        assertTrue(controller.contains("ROLE_CONTENT_MODERATOR"), "content moderators must be able to inspect history");
        assertTrue(facade.contains("if (!moderator && !post.getAuthorId().equals(viewerUid))"), "non-author non-moderator viewers must be rejected");
        assertTrue(facade.contains("throw new BizException(ErrorCode.FORBIDDEN)"), "forbidden history reads must fail explicitly");
        assertTrue(facade.contains("versionHistoryService.listRecent(postId, limit)"), "authorized reads must use the bounded history service");

        for (String field : new String[] {"authorId", "editorUid", "baseVersion", "contentSummary", "tags", "changeSummary", "createTime"}) {
            assertTrue(dto.contains(field), "version DTO must expose " + field);
        }
        assertTrue(po.contains("@TableName(\"t_post_version_history\")"), "history PO must map to the history table");
        assertTrue(mapper.contains("ORDER BY create_time DESC, id DESC"), "history query must return newest snapshots first");
        assertTrue(mapper.contains("LIMIT #{limit}"), "history query must enforce the requested bounded limit");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
