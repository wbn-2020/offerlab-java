package com.offerlab.community.post.application;

import com.offerlab.community.post.controller.KnowledgeController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeRelationPublicContractTest {

    @Test
    void publicKnowledgeRelationsContractDoesNotExposeSeriesSeed() {
        Method controllerMethod = Arrays.stream(KnowledgeController.class.getDeclaredMethods())
                .filter(method -> "relations".equals(method.getName()))
                .findFirst()
                .orElseThrow();
        Method serviceMethod = Arrays.stream(KnowledgeRelationService.class.getDeclaredMethods())
                .filter(method -> "explore".equals(method.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(5, controllerMethod.getParameterCount(),
                "public knowledge controller should only accept postId/tagId/topicId/domain/limit");
        assertEquals(5, serviceMethod.getParameterCount(),
                "knowledge relation service should align with the public seed contract");
    }

    @Test
    void knowledgeRelationGraphMustStayPublicAndGoverned() throws Exception {
        String service = Files.readString(Path.of("src/main/java/com/offerlab/community/post/application/KnowledgeRelationService.java"), StandardCharsets.UTF_8);
        String topicTagMapper = Files.readString(Path.of("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/CommunityTopicTagMapper.java"), StandardCharsets.UTF_8);

        assertTrue(service.contains("postFacade.batchGetPosts(postIds, null, false)"),
                "knowledge relation post nodes must pass through public PostFacade visibility and governance filters");
        assertTrue(service.contains("isOnlineTopic(topic)"),
                "knowledge relation topic seeds and expansions must filter offline/deleted topics");
        assertTrue(service.contains("isPublicTag(tag)"),
                "knowledge relation tag nodes must filter disabled/merged/unsafe tags");
        assertTrue(service.contains("PublicContentFilter.isUnsafeSuggestionText"),
                "knowledge relation node labels must reuse public governance text filters");
        assertTrue(topicTagMapper.contains("t.tag_status = 1"),
                "topic tag expansion must only return active tags");
        assertTrue(topicTagMapper.contains("t.merge_target_id IS NULL"),
                "topic tag expansion must not return merged tags");
    }
}
