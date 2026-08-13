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
        String validator = read("src/main/java/com/offerlab/community/post/application/PostPublishQualityValidator.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostDraftMapper.java");
        String po = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/po/PostDraftPO.java");
        String dto = read("src/main/java/com/offerlab/community/post/api/dto/PostDraftDTO.java");
        String cmd = read("src/main/java/com/offerlab/community/post/api/dto/PostDraftCmd.java");
        String createCmd = read("src/main/java/com/offerlab/community/post/api/dto/PostCreateCmd.java");
        String appService = read("src/main/java/com/offerlab/community/post/application/PostApplicationService.java");
        String limits = read("src/main/java/com/offerlab/community/post/api/dto/PostContentLimits.java");

        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS t_post_draft"), "migration must create the draft table");
        assertTrue(migration.contains("source_post_id"), "drafts for editing existing posts must keep source_post_id");
        assertTrue(migration.contains("idx_uid_update_time"), "recent draft list must have a user/update-time index");
        assertTrue(migration.contains("idx_uid_source_post"), "source-post draft lookup must have a user/source index");
        assertFalse(migration.toLowerCase().contains("drop table"), "non-destructive migration must not drop tables");
        assertTrue(init.contains("CREATE TABLE t_post_draft"), "fresh local schema must include t_post_draft");

        assertTrue(po.contains("@TableName(\"t_post_draft\")"), "PO must map to t_post_draft");
        assertTrue(po.contains("@TableLogic"), "draft deletes must be logical deletes");
        assertTrue(dto.contains("private Long sourcePostId"), "draft DTO must expose sourcePostId");
        assertTrue(dto.contains("private Integer domain"), "draft DTO must expose explicit domain");
        assertTrue(dto.contains("private Boolean anonymous"), "draft DTO must expose explicit anonymous");
        assertTrue(dto.contains("private List<Long> tagIds"), "draft DTO must preserve tag ids");
        assertTrue(dto.contains("private List<String> tagNames"), "draft DTO must preserve tag names");
        assertTrue(cmd.contains("private Long sourcePostId"), "draft command must accept sourcePostId");
        assertTrue(cmd.contains("private Integer domain"), "draft command must accept explicit domain");
        assertTrue(cmd.contains("private Boolean anonymous"), "draft command must accept explicit anonymous");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/post-drafts\")"), "draft API path must stay stable");
        assertTrue(controller.contains("public Result<List<PostDraftDTO>> list"), "draft API must list recent drafts");
        assertTrue(controller.contains("public Result<PostDraftDTO> latestBySource"), "draft API must load latest source-post draft");
        assertTrue(controller.contains("UserContext.require()"), "draft APIs must be user-owned");
        assertTrue(controller.contains("private Integer domain"), "draft request must accept explicit domain");
        assertTrue(controller.contains("private Boolean anonymous"), "draft request must accept explicit anonymous");
        assertTrue(controller.contains("@Size(max = PostContentLimits.MAX_TAG_COUNT)\n        private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long> tags;"),
                "legacy draft tags field must keep the same DTO-level tag id cap and element guard");
        assertTrue(controller.contains("@Size(max = PostContentLimits.MAX_TAG_COUNT)\n        private List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long> tagIds;"),
                "draft tagIds must cap selections and reject invalid ids at DTO level");
        assertTrue(controller.contains("@Size(max = PostContentLimits.MAX_TAG_COUNT)\n        private List<@Size(max = PostContentLimits.MAX_TAG_NAME_LEN) String> tagNames;"),
                "draft tagNames must cap selections and bound each tag name at DTO level");
        assertTrue(controller.contains("requireOptionalDomain(req.getDomain())"), "draft request domain must use the shared domain validator");
        assertTrue(controller.contains("draftService.save"), "draft save endpoints must call the service");
        assertTrue(controller.contains("draftService.delete"), "draft delete endpoint must call the service");

        assertTrue(service.contains("selectRecentByUser"), "service must list drafts by current user");
        assertTrue(service.contains("selectByUser"), "service must load drafts by owner");
        assertTrue(service.contains("selectLatestBySourcePost"), "service must support edit-draft recovery");
        assertTrue(service.contains("normalizeDraftExtJson"), "draft service must normalize explicit domain/anonymous into extJson");
        assertTrue(service.contains("draftDomain"), "draft service must recover domain from explicit fields or legacy extJson");
        assertTrue(service.contains("draftAnonymous"), "draft service must recover anonymous from explicit fields or legacy extJson");
        assertTrue(service.contains(".domain("), "draft DTO builder must return domain");
        assertTrue(service.contains(".anonymous("), "draft DTO builder must return anonymous");
        assertTrue(service.contains("deleteIfOwned"), "publish/update flow must be able to clear owned drafts");
        assertTrue(service.contains("new LambdaUpdateWrapper<PostDraftPO>()"), "draft deletion must constrain by id and uid");
        assertFalse(appService.contains("readDomainFromExtJson"),
                "publishing must not recover a missing domain from legacy draft extJson");
        assertTrue(appService.contains("requirePublishDomain(cmd.getDomain())"),
                "new publishing must require an explicit valid domain");

        assertTrue(mapper.contains("WHERE uid = #{uid}"), "mapper queries must be scoped by uid");
        assertTrue(mapper.contains("AND is_deleted = 0"), "mapper queries must ignore deleted drafts");
        assertTrue(postController.contains("private Long draftId"), "publish/update requests must accept draftId");
        assertTrue(postController.contains("draftService.deleteIfOwned(uid, req.getDraftId())"), "publishing must clear only the caller's draft");
        assertTrue(limits.contains("MAX_CONTENT_LEN = 50000"), "post body limit must match the frontend editor");
        assertTrue(limits.contains("MAX_EXT_JSON_LEN = 20000"), "extension JSON limit must remain bounded separately");
        assertTrue(limits.contains("MAX_TAG_COUNT = 5"), "post and draft APIs must match the five-tag editor contract");
        assertTrue(service.contains("requireLimit("),
                "draft persistence must reject over-limit content instead of silently truncating it");
        assertFalse(service.contains("normalized.substring(0, max)"),
                "draft persistence must never silently truncate user content");
        assertTrue(cmd.contains("@Size(max = PostContentLimits.MAX_CONTENT_LEN)"), "draft command must use the shared body limit");
        assertTrue(createCmd.contains("@Size(max = PostContentLimits.MAX_CONTENT_LEN)"), "publish command must use the shared body limit");
        assertTrue(controller.contains("@Size(max = PostContentLimits.MAX_CONTENT_LEN)"), "draft request must use the shared body limit");
        assertTrue(postController.contains("@Size(max = PostContentLimits.MAX_CONTENT_LEN)"), "publish/update requests must use the shared body limit");
        assertTrue(service.contains("PostContentLimits.MAX_CONTENT_LEN"), "draft persistence must not truncate below the shared body limit");
        assertTrue(validator.contains("MAX_CONTENT_LEN = PostContentLimits.MAX_CONTENT_LEN"), "quality validation must use the shared body limit");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
