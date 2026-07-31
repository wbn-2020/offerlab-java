package com.offerlab.community.post.knowledge.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostKnowledgeRelationContractGuardTest {

    @Test
    void publicReadAndReviewQueueContractsStayStrong() throws Exception {
        String mapper = read("src/main/java/com/offerlab/community/post/knowledge/infrastructure/PostKnowledgeRelationMapper.java");
        String service = read("src/main/java/com/offerlab/community/post/knowledge/application/PostKnowledgeRelationService.java");
        String controller = read("src/main/java/com/offerlab/community/post/knowledge/controller/PostKnowledgeRelationController.java");
        String handler = read("src/main/java/com/offerlab/community/post/knowledge/application/PostKnowledgeRelationQueueActionHandler.java");
        String resolver = read("src/main/java/com/offerlab/community/post/knowledge/application/PostKnowledgeRelationReviewQueueSourceDomainResolver.java");

        assertTrue(mapper.contains("r.review_status = 'APPROVED'"));
        assertTrue(mapper.contains("r.visibility_status = 'VISIBLE'"));
        assertTrue(mapper.contains("r.is_deleted = 0"));
        assertTrue(mapper.contains("source_post.post_status = 1"));
        assertTrue(mapper.contains("source_post.visibility = 1"));
        assertTrue(mapper.contains("target_post.post_status = 1"));
        assertTrue(mapper.contains("target_post.visibility = 1"));
        assertTrue(mapper.contains("review_status IN ('PENDING', 'APPROVED')"));
        assertTrue(mapper.contains("proposer_uid <> #{reviewerUid}"));

        assertTrue(service.contains("PostKnowledgeRelationType.CONTRADICTS"));
        assertTrue(service.contains("source.getId() > target.getId()"));
        assertTrue(service.contains("mapper.findEffective"));
        assertTrue(service.contains("catch (DuplicateKeyException"));
        assertTrue(service.contains("requestedTarget.isVisibleTo(null, false)"));
        assertTrue(service.contains("\"KNOWLEDGE_RELATION\""));
        assertFalse(service.contains("DomainModeratorService"));

        assertTrue(controller.contains("@PublicApi"));
        // Every endpoint must be rate limited, however many endpoints exist.
        int endpoints = count(controller, "@GetMapping") + count(controller, "@PostMapping")
                + count(controller, "@PutMapping") + count(controller, "@DeleteMapping");
        assertTrue(endpoints >= 2, "controller must expose the propose + public read endpoints");
        assertEquals(endpoints, count(controller, "@RateLimit"),
                "every knowledge-relation endpoint must carry @RateLimit");
        assertFalse(controller.contains("/review"));
        assertTrue(handler.contains("implements ReviewQueueSourceActionHandler"));
        assertTrue(resolver.contains("implements ReviewQueueSourceDomainResolver"));
        assertTrue(resolver.contains("relation.getSourcePostId()"));
        assertFalse(handler.contains("DomainModeratorService"));
        assertFalse(resolver.contains("DomainModeratorService"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static int count(String source, String token) {
        return source.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
