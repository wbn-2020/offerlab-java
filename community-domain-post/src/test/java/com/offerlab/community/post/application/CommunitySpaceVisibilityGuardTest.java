package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunitySpaceVisibilityGuardTest {

    @Test
    void publicSpacesReuseVisibilityRulesAndIsolateOptionalSources() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/controller/CommunitySpaceController.java");
        String service = read("src/main/java/com/offerlab/community/post/application/CommunitySpaceQueryService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/CommunitySpaceMapper.java");
        String resource = read("src/main/java/com/offerlab/community/post/application/PublicUpdateResourceService.java");

        assertTrue(controller.contains("@PublicApi"));
        assertTrue(controller.contains("@RateLimit"));
        assertTrue(controller.contains("UserContext.get()"));
        assertTrue(controller.contains("/topics/{slug}"));
        assertTrue(controller.contains("/collections/{seriesId}"));
        assertTrue(controller.contains("/series/{seriesId}"));

        assertTrue(mapper.contains("moderation_hidden = 0"));
        assertTrue(mapper.contains("FROM t_collab_series"));
        assertTrue(mapper.contains("FROM t_content_series_post"));
        assertTrue(mapper.contains("s.visibility = 1"));
        assertTrue(mapper.contains("s.is_deleted = 0"));
        assertTrue(mapper.contains("p.is_deleted = 0"));
        assertTrue(mapper.contains("p.post_status = 1"));
        assertTrue(mapper.contains("p.visibility = 1"));
        assertTrue(mapper.contains("ORDER BY n.id DESC"));
        assertTrue(mapper.contains("LIMIT #{limit}"));
        assertFalse(mapper.contains("review_note"));
        assertFalse(mapper.contains("delivery_note"));

        assertTrue(service.contains("degradedSources"));
        assertTrue(service.contains("relatedNeeds"));
        assertTrue(service.contains("relatedContributions"));
        assertTrue(service.contains("knowledgeRelationService"));
        assertTrue(service.contains("discoveryMapService"));
        assertTrue(service.contains("postFacade.listPublicUpdates"));
        assertTrue(service.contains("listPublicNeedsByCollaborationSeries"));
        assertTrue(service.contains("listPublicNeedsByPosts"));
        assertTrue(service.contains("listPublicSeriesContributions"));
        assertTrue(service.contains("topicService.listPosts(slug, null, null, cursor, pageSize(size), null)"));

        assertTrue(resource.contains("postFacade.getPost(postId, null)"));
        assertTrue(resource.contains("topicService.getPublic(topicSlug, null)"));
        assertTrue(resource.contains("contentSeriesService.getPublicDetail(seriesId)"));
        assertTrue(resource.contains("resolveCollaborationSeries"));
        assertTrue(resource.contains("resolveCollection"));
        assertTrue(resource.contains("publicTopicContainsPost"));
        assertTrue(mapper.contains("int publicTopicContainsPost"));
        assertTrue(mapper.contains("t_community_topic_tag"));
        assertTrue(mapper.contains("t_post_tag_ref"));
        assertTrue(resource.contains("publicContentSeriesContainsPost"));
        assertTrue(resource.contains("publicCollaborationSeriesContainsPost"));
        assertTrue(resource.contains("collaborationService.getNeed(needId, null)"));
        assertFalse(resource.contains("case \"SERIES\", \"COLLECTION\""));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
