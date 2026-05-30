package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostDraftGuardTest {

    @Test
    void postDraftsMustRemainServerBackedAndOwnedByUser() throws Exception {
        String migration = read("../db/migration/20260530_post_draft.sql");
        String init = read("../db/init/02_post.sql");
        String controller = read("src/main/java/com/offerlab/community/post/controller/PostDraftController.java");
        String postController = read("src/main/java/com/offerlab/community/post/controller/PostController.java");
        String service = read("src/main/java/com/offerlab/community/post/application/PostDraftService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostDraftMapper.java");
        String po = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/po/PostDraftPO.java");
        String dto = read("src/main/java/com/offerlab/community/post/api/dto/PostDraftDTO.java");
        String cmd = read("src/main/java/com/offerlab/community/post/api/dto/PostDraftCmd.java");

        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS t_post_draft"), "migration must create the draft table");
        assertTrue(migration.contains("source_post_id"), "drafts for editing existing posts must keep source_post_id");
        assertTrue(migration.contains("idx_uid_update_time"), "recent draft list must have a user/update-time index");
        assertTrue(migration.contains("idx_uid_source_post"), "source-post draft lookup must have a user/source index");
        assertFalse(migration.toLowerCase().contains("drop table"), "non-destructive migration must not drop tables");
        assertTrue(init.contains("CREATE TABLE t_post_draft"), "fresh local schema must include t_post_draft");

        assertTrue(po.contains("@TableName(\"t_post_draft\")"), "PO must map to t_post_draft");
        assertTrue(po.contains("@TableLogic"), "draft deletes must be logical deletes");
        assertTrue(dto.contains("private Long sourcePostId"), "draft DTO must expose sourcePostId");
        assertTrue(dto.contains("private List<Long> tagIds"), "draft DTO must preserve tag ids");
        assertTrue(dto.contains("private List<String> tagNames"), "draft DTO must preserve tag names");
        assertTrue(cmd.contains("private Long sourcePostId"), "draft command must accept sourcePostId");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/post-drafts\")"), "draft API path must stay stable");
        assertTrue(controller.contains("public Result<List<PostDraftDTO>> list"), "draft API must list recent drafts");
        assertTrue(controller.contains("public Result<PostDraftDTO> latestBySource"), "draft API must load latest source-post draft");
        assertTrue(controller.contains("UserContext.require()"), "draft APIs must be user-owned");
        assertTrue(controller.contains("draftService.save"), "draft save endpoints must call the service");
        assertTrue(controller.contains("draftService.delete"), "draft delete endpoint must call the service");

        assertTrue(service.contains("selectRecentByUser"), "service must list drafts by current user");
        assertTrue(service.contains("selectByUser"), "service must load drafts by owner");
        assertTrue(service.contains("selectLatestBySourcePost"), "service must support edit-draft recovery");
        assertTrue(service.contains("deleteIfOwned"), "publish/update flow must be able to clear owned drafts");
        assertTrue(service.contains("new LambdaUpdateWrapper<PostDraftPO>()"), "draft deletion must constrain by id and uid");

        assertTrue(mapper.contains("WHERE uid = #{uid}"), "mapper queries must be scoped by uid");
        assertTrue(mapper.contains("AND is_deleted = 0"), "mapper queries must ignore deleted drafts");
        assertTrue(postController.contains("private Long draftId"), "publish/update requests must accept draftId");
        assertTrue(postController.contains("draftService.deleteIfOwned(uid, req.getDraftId())"), "publishing must clear only the caller's draft");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
