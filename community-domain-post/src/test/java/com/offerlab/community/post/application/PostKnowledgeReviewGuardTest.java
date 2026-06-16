package com.offerlab.community.post.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PostKnowledgeReviewGuardTest {

    private static final Path ROOT = Path.of(".").toAbsolutePath().normalize();

    @Test
    void knowledgeReviewMustBeAuditedPermissionedAndPersistedToPostExtension() throws Exception {
        String controller = read(ROOT.resolve("src/main/java/com/offerlab/community/post/controller/PostController.java"));
        String service = read(ROOT.resolve("src/main/java/com/offerlab/community/post/application/PostKnowledgeReviewService.java"));
        String api = read(Path.of("../../offerlab-vue/src/api/post.ts"));
        String opsView = read(Path.of("../../offerlab-vue/src/views/OpsView.vue"));

        assertTrue(controller.contains("@PostMapping(\"/admin/knowledge/{postId}/review\")"), "post controller must expose knowledge review endpoint");
        assertTrue(controller.contains("ROLE_CONTENT_MODERATOR"), "knowledge review endpoint must require content moderator scope");
        assertTrue(controller.contains("KnowledgeReviewReq"), "knowledge review endpoint must use a typed request");

        assertTrue(service.contains("recordRequired"), "knowledge review must fail closed when admin audit is unavailable");
        assertTrue(service.contains("POST_KNOWLEDGE_REVIEW_APPLY"), "knowledge review must write admin audit logs");
        assertTrue(service.contains("knowledgeReviewed"), "knowledge review must mark reviewed content");
        assertTrue(service.contains("knowledgeReviewedAt"), "knowledge review must record review time");
        assertTrue(service.contains("knowledgeReviewedBy"), "knowledge review must record reviewer");
        assertTrue(service.contains("summary"), "knowledge review must write summary");
        assertTrue(service.contains("faqJson"), "knowledge review must write FAQ JSON");
        assertTrue(service.contains("knowledgeCardJson"), "knowledge review must write knowledge cards");
        assertTrue(service.contains("techStacks"), "knowledge review must write tech stacks");
        assertTrue(service.contains("suggestedTags"), "knowledge review must write suggested tags");
        assertTrue(service.contains("CacheKeyBuilder.postDetail(postId)"), "knowledge review must evict rendered post detail cache");
        assertTrue(service.contains("CacheKeyBuilder.postDetailRaw(postId)"), "knowledge review must evict raw post detail cache");
        assertTrue(service.contains("AdminAuditService"), "knowledge review must use admin audit service");

        assertTrue(api.contains("PostKnowledgeReviewReq"), "frontend post API must type knowledge review request");
        assertTrue(api.contains("/api/v1/posts/admin/knowledge/${postId}/review"), "frontend post API must call knowledge review endpoint");
        assertTrue(opsView.contains("知识沉淀回写"), "Ops AI detail must expose knowledge review form");
        assertTrue(opsView.contains("reviewKnowledge"), "Ops AI detail must submit knowledge review");
        assertTrue(opsView.contains("requireRiskConfirm"), "knowledge review must require risk confirmation");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
