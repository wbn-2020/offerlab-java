package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase17KnowledgeEntryGuardTest {

    @Test
    void knowledgeExploreMustOnlyExposeCurrentPublicPostsTopicsAndTags() throws Exception {
        String service = read("src/main/java/com/offerlab/community/post/application/KnowledgeRelationService.java");

        assertTrue(service.contains("postMapper.selectPublicPosts("),
                "Knowledge explore must seed from public post queries, not raw private post tables");
        assertTrue(service.contains("postMapper.selectPublicPostsByTopic("),
                "Topic knowledge explore must seed from public topic post queries");
        assertTrue(service.contains("postFacade.batchGetPosts(postIds, null, false)"),
                "Knowledge explore must re-check current post visibility before building graph nodes");
        assertTrue(service.contains("selectOnlineTopicsByTagIds(tagIds, limit)"),
                "Knowledge explore must only add online topics");
        assertTrue(service.contains("isOnlineTopic(topic)") && service.contains("!Integer.valueOf(1).equals(topic.getIsDeleted())")
                        && service.contains("Integer.valueOf(1).equals(topic.getTopicStatus())"),
                "Knowledge explore must filter deleted, offline, synthetic, and unsafe topics");
        assertTrue(service.contains("isPublicTag(tag)") && service.contains("getMergeTargetId()"),
                "Knowledge explore must filter disabled, merged, synthetic, and unsafe tags");
        assertFalse(service.contains("batchGetPosts(postIds, null, true)"),
                "Knowledge explore must not include test/private data in public entry graph");
    }

    @Test
    void publicSeriesAndInvisibleHomeCollectionsMustStayOutOfDiscovery() throws Exception {
        String service = read("src/main/java/com/offerlab/community/post/application/ContentSeriesService.java");
        String mapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/ContentSeriesMapper.java");
        String relationMapper = read("src/main/java/com/offerlab/community/post/infrastructure/persistence/mapper/ContentSeriesPostMapper.java");

        assertTrue(service.contains("requirePublicSeries(seriesId)"),
                "Public collection detail and post listing must require a public visible series");
        assertTrue(service.contains("contentSeriesMapper.selectPublicByCreatorUid"),
                "Author public collection discovery must use the public mapper path");
        assertTrue(service.contains("postMapper.selectPublicPostsByContentSeries"),
                "Collection post discovery must query public posts only");
        assertTrue(service.contains("postFacade.batchGetPosts(") && service.contains("pagePosts.stream().map(PostPO::getId).toList(), null, false"),
                "Collection posts must be reloaded through current public post visibility");
        assertTrue(mapper.contains("AND visibility = 1") && mapper.contains("AND is_deleted = 0"),
                "Public collection mapper paths must exclude private and deleted collections");
        assertTrue(mapper.contains("p.post_status = 1") && mapper.contains("p.visibility = 1") && mapper.contains("p.is_deleted = 0"),
                "Collection progress and post counts must not count private, deleted, or unpublished posts");
        assertTrue(relationMapper.contains("WHERE is_deleted = 0"),
                "Collection relation mapper must ignore deleted relations");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
