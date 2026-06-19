package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityTopicGuardTest {

    private static final Path ROOT = Path.of(System.getProperty("user.dir")).getParent();

    @Test
    void realCommunityTopicModelKeepsTablesApisPermissionAndAudit() throws Exception {
        String migration = read(ROOT.resolve("db/migration/20260608_community_topics.sql"));
        assertContains(migration, "CREATE TABLE IF NOT EXISTS t_community_topic");
        assertContains(migration, "CREATE TABLE IF NOT EXISTS t_community_topic_tag");
        assertContains(migration, "CREATE TABLE IF NOT EXISTS t_community_topic_follow");
        assertContains(migration, "uk_topic_slug");
        assertContains(migration, "idx_topic_status_sort");
        assertContains(migration, "idx_topic_featured_sort");
        assertContains(migration, "idx_topic_tag_topic");
        assertContains(migration, "idx_topic_tag_tag");
        assertContains(migration, "uk_topic_follow_user");
        assertContains(migration, "idx_topic_follow_uid");
        assertContains(migration, "idx_topic_follow_topic");

        String init = read(ROOT.resolve("db/init/02_post.sql"));
        assertContains(init, "CREATE TABLE t_community_topic");
        assertContains(init, "CREATE TABLE t_community_topic_tag");
        assertContains(init, "CREATE TABLE t_community_topic_follow");

        String controller = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/controller/CommunityTopicController.java"));
        assertContains(controller, "@RequestMapping(\"/api/v1/topics\")");
        assertContains(controller, "@GetMapping(\"/{slug}/posts\")");
        assertContains(controller, "@GetMapping(\"/me/following\")");
        assertContains(controller, "@GetMapping(\"/{slug}/follow-status\")");
        assertContains(controller, "@PostMapping(\"/{slug}/follow\")");
        assertContains(controller, "@DeleteMapping(\"/{slug}/follow\")");
        assertContains(controller, "@GetMapping(\"/admin\")");
        assertContains(controller, "@PostMapping(\"/admin\")");
        assertContains(controller, "@PutMapping(\"/admin/{topicId}\")");
        assertContains(controller, "@PostMapping(\"/admin/{topicId}/status\")");
        assertContains(controller, "AdminPermissionService.ROLE_CONTENT_MODERATOR");

        String service = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/CommunityTopicService.java"));
        assertContains(service, "auditService.recordRequired");
        assertContains(service, "COMMUNITY_TOPIC_CREATE");
        assertContains(service, "COMMUNITY_TOPIC_UPDATE");
        assertContains(service, "COMMUNITY_TOPIC_ONLINE");
        assertContains(service, "COMMUNITY_TOPIC_OFFLINE");
        assertContains(service, "selectPublicPostsByTopic");
        assertContains(service, "listFollowingTopics");
        assertContains(service, "topicFollowMapper.selectFollowingTopics");
        assertContains(service, "topicFollowMapper.restoreById");
        assertContains(service, "topicFollowMapper.softDeleteById");
        assertContains(service, "resolveTopicForRead");
        assertContains(service, "virtualTopicDto");
        assertContains(service, "topicDisplayName");
        assertContains(service, "migrationCheckService.communityTopicReady()");
        assertContains(service, "return List.of();");
        assertContains(service, "return PageResult.empty();");
        assertContains(service, "社区专题数据库迁移未完成");
        assertContains(service, "db/migration/20260608_community_topics.sql");

        String topicFollowMapper = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/CommunityTopicFollowMapper.java"));
        assertContains(topicFollowMapper, "t_community_topic_follow");
        assertContains(topicFollowMapper, "selectFollowingTopics");
        assertContains(topicFollowMapper, "selectFollowedTopicIds");

        String dto = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/api/dto/CommunityTopicDTO.java"));
        assertContains(dto, "private Long followerCount");
        assertContains(dto, "private Boolean followed");
        assertContains(dto, "private Boolean virtualTopic");

        String topicMapper = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/CommunityTopicMapper.java"));
        assertContains(topicMapper, "selectBySlugOrName");
        assertContains(topicMapper, "LOWER(slug) = LOWER(#{slug})");

        String postMapper = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/PostMapper.java"));
        assertContains(postMapper, "List<PostPO> selectPublicPostsByTopic");
        assertContains(postMapper, "<if test=\"tagIds != null and tagIds.size() > 0\">");
        assertNotContains(postMapper.replace("\r\n", "\n"), "<if test=\"topicId != null\">\n                    <if test=\"tagIds != null and tagIds.size() > 0\">");

        String migrationCheck = read(ROOT.resolve("community-infrastructure/src/main/java/com/offerlab/community/infra/db/MigrationCheckService.java"));
        assertContains(migrationCheck, "public boolean communityTopicReady()");
        assertContains(migrationCheck, "t_community_topic");
        assertContains(migrationCheck, "t_community_topic_tag");
        assertContains(migrationCheck, "t_community_topic_follow");
        assertContains(migrationCheck, "idx_topic_featured_sort");
        assertContains(migrationCheck, "idx_topic_tag_topic");
        assertContains(migrationCheck, "idx_topic_follow_uid");
        assertContains(migrationCheck, "20260608_community_topics.sql");
        assertContains(migrationCheck, "public boolean reviewQueueReady()");
        assertContains(migrationCheck, "t_review_queue");
        assertContains(migrationCheck, "idx_review_queue_status_priority");

        String schemaScript = read(ROOT.resolve("scripts/check-schema-readiness.mjs"));
        assertContains(schemaScript, "idx_topic_featured_sort");
        assertContains(schemaScript, "idx_topic_tag_topic");
        assertContains(schemaScript, "idx_topic_follow_uid");
    }

    @Test
    void topicPostListMustReusePostFacadeBriefAssemblyForAnonymousMasking() throws Exception {
        String controller = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/controller/CommunityTopicController.java"));
        String service = read(ROOT.resolve("community-domain-post/src/main/java/com/offerlab/community/post/application/CommunityTopicService.java"));

        assertContains(service, "private final PostFacade postFacade");
        assertContains(service, "PageResult<PostBriefDTO> listPosts(String slug, Integer postType, Boolean featured, long cursor, int size, Long viewerUid)");
        assertContains(service, "postFacade.batchGetPosts(postIds, viewerUid)");
        assertContains(controller, "topicService.listPosts(slug, type, featured, cursor, size, UserContext.get())");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected source to contain: " + expected);
    }

    private static void assertNotContains(String source, String unexpected) {
        assertFalse(source.contains(unexpected), () -> "Expected source not to contain: " + unexpected);
    }
}
