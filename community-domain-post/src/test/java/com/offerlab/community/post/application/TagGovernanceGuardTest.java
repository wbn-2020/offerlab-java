package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TagGovernanceGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void tagGovernanceKeepsAdminApisAuditAndDisabledTagRules() throws Exception {
        String migration = read(ROOT.resolve("db/migration/20260608_tag_governance.sql"));
        assertContains(migration, "tag_status");
        assertContains(migration, "recommended");
        assertContains(migration, "synonyms");
        assertContains(migration, "merge_target_id");
        assertContains(migration, "idx_tag_status_recommend");

        String init = read(ROOT.resolve("db/init/02_post.sql"));
        assertContains(init, "tag_status");
        assertContains(init, "recommended");
        assertContains(init, "synonyms");
        assertContains(init, "merge_target_id");

        String controller = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/controller/TagController.java"));
        assertContains(controller, "@GetMapping(\"/admin\")");
        assertContains(controller, "@PutMapping(\"/admin/{tagId}\")");
        assertContains(controller, "@PostMapping(\"/admin/{tagId}/status\")");
        assertContains(controller, "@PostMapping(\"/admin/{tagId}/recommend\")");
        assertContains(controller, "@PostMapping(\"/admin/{tagId}/synonyms\")");
        assertContains(controller, "@PostMapping(\"/admin/{tagId}/merge\")");
        assertContains(controller, "AdminPermissionService.ROLE_CONTENT_MODERATOR");

        String service = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/TagGovernanceService.java"));
        assertContains(service, "RiskConfirmation.requireHigh(cmd.getNote())");
        assertContains(service, "RiskConfirmation.requireHigh(note)");
        assertContains(service, "RiskConfirmation.requireCritical(note, confirmationPhrase)");
        assertContains(service, "auditService.recordRequired");
        assertContains(service, "TAG_GOVERNANCE_UPDATE");
        assertContains(service, "TAG_DISABLE");
        assertContains(service, "TAG_RECOMMEND");
        assertContains(service, "TAG_SYNONYMS_UPDATE");
        assertContains(service, "TAG_MERGE");
        assertContains(service, "deleteDuplicatePostTagRefsForMerge");
        assertContains(service, "deleteDuplicateTopicTagRefsForMerge");
        assertContains(service, "migrationCheckService.communityTopicReady()");
        assertContains(service, "topicMergeSkipped");

        String mapper = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/TagMapper.java"));
        assertContains(mapper, "selectGovernanceTags");
        assertContains(mapper, "tag_status = 1");
        assertContains(mapper, "updateGovernance");
        assertContains(mapper, "markMerged");

        String postService = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/PostApplicationService.java"));
        assertContains(postService, "标签不存在、已删除或已被禁用");
        assertContains(postService, "migrationCheckService.tagGovernanceReady()");
        assertContains(postService, "tagMapper.selectByIdsCompat(tagIds)");
        assertContains(postService, "tagMapper.selectTagsByPostIdsCompat(postIds)");
        assertContains(postService, "tagMapper.selectByNamesCompat(names)");
        assertContains(postService, "tagMapper.insertIgnoreNameCompat(id, name, tagType)");

        String postController = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/controller/PostController.java"));
        assertContains(postController, "private List<String> tagNames");
        assertContains(postController, ".tagNames(req.getTagNames())");

        String postFacade = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/PostFacadeImpl.java"));
        assertContains(postFacade, "tagMapper.selectActiveTagsCompat()");
        assertContains(postFacade, "tagMapper.selectTagsByPostIdsCompat(postIds)");

        String topicService = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/CommunityTopicService.java"));
        assertContains(topicService, "专题不能绑定已禁用标签");
        assertContains(topicService, "tagMapper.selectByNamesCompat(names)");

        String searchFacade = read(ROOT.resolve("community-domain-search/src/main/java/com/offerlab/community/search/application/SearchFacadeImpl.java"));
        assertContains(searchFacade, "postMapper.searchPublicPostsFallbackCompat");
        assertContains(searchFacade, "postMapper.suggestPublicPostsFallbackCompat");
        assertContains(searchFacade, "tagMapper.selectTagsByPostIdsCompat(postIds)");

        String postMapper = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java"));
        assertContains(postMapper, "searchPublicPostsFallbackCompat");
        assertContains(postMapper, "suggestPublicPostsFallbackCompat");
        assertNotContains(methodBlock(postMapper, "searchPublicPostsFallbackCompat"), "synonyms");
        assertNotContains(methodBlock(postMapper, "suggestPublicPostsFallbackCompat"), "synonyms");

        String migrationCheck = read(ROOT.resolve("community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java"));
        assertContains(migrationCheck, "tagGovernanceReady()");
        assertContains(migrationCheck, "columnExists(\"t_tag\", \"tag_status\")");

        String dto = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/api/dto/TagDTO.java"));
        assertContains(dto, "private Integer status");
        assertContains(dto, "private Boolean recommended");
        assertContains(dto, "private Long mergeTargetId");
        assertContains(dto, "private java.util.List<String> synonyms");

        String cmd = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/api/dto/TagGovernanceCmd.java"));
        assertContains(cmd, "private String confirmationPhrase");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }

    private static void assertNotContains(String source, String forbidden) {
        assertTrue(!source.contains(forbidden), () -> "Expected source not to contain: " + forbidden);
    }

    private static String methodBlock(String source, String methodName) {
        int signature = source.indexOf(methodName + "(");
        if (signature < 0) {
            signature = source.indexOf(methodName);
        }
        assertTrue(signature >= 0, () -> "Expected method to exist: " + methodName);
        int start = source.lastIndexOf("@Select", signature);
        int next = source.indexOf("@Select", signature + methodName.length());
        if (next < 0) {
            next = source.indexOf("@Insert", signature + methodName.length());
        }
        if (next < 0) {
            next = source.indexOf("@Update", signature + methodName.length());
        }
        if (next < 0) {
            next = source.indexOf("@Delete", signature + methodName.length());
        }
        return source.substring(Math.max(0, start), next < 0 ? source.length() : next);
    }
}
