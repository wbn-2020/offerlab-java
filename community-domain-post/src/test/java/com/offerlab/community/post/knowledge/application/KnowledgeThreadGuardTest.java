package com.offerlab.community.post.knowledge.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeThreadGuardTest {

    @Test
    void readingThreadStaysBoundedPublicFilteredAndReadOnly() throws Exception {
        String service = Files.readString(
                Path.of("src/main/java/com/offerlab/community/post/knowledge/application/PostKnowledgeRelationService.java"),
                StandardCharsets.UTF_8);
        String mapper = Files.readString(
                Path.of("src/main/java/com/offerlab/community/post/knowledge/infrastructure/PostKnowledgeRelationMapper.java"),
                StandardCharsets.UTF_8);
        String controller = Files.readString(
                Path.of("src/main/java/com/offerlab/community/post/knowledge/controller/PostKnowledgeRelationController.java"),
                StandardCharsets.UTF_8);

        // Bounded walk: explicit hop and node caps, wired into the traversal.
        assertTrue(service.contains("MAX_THREAD_HOPS = 3"), "hop bound must exist");
        assertTrue(service.contains("MAX_THREAD_NODES = 15"), "node bound must exist");
        assertTrue(service.contains("hop <= MAX_THREAD_HOPS"), "walk must consume the hop bound");
        assertTrue(service.contains("visited.size() >= MAX_THREAD_NODES"), "walk must consume the node bound");
        assertTrue(service.contains("truncated[0] = true"), "cut chains must be reported honestly");

        // Every hop uses the dedicated public chain query and asks for one
        // sentinel row so mapper LIMIT truncation is observable.
        int walkStart = service.indexOf("private ThreadSelection nextStep(");
        assertTrue(walkStart >= 0, "nextStep must exist");
        String walkBody = service.substring(walkStart, service.indexOf("private static Long chainNeighbor(", walkStart));
        assertTrue(walkBody.contains("mapper.listPublicChainByPostId("),
                "thread hops must use the public-filtered chain query");
        assertTrue(walkBody.contains("THREAD_RELATIONS_PER_HOP + 1"),
                "thread hops must fetch a sentinel row beyond the processing limit");
        assertFalse(walkBody.contains("findActiveById") || walkBody.contains("findEffective"),
                "thread hops must not bypass the public filter with raw row lookups");
        assertTrue(walkBody.contains("isVisibleTo(null, false)"),
                "each neighbor post must pass the anonymous visibility check");
        assertTrue(walkBody.contains("safeRows.size() > THREAD_RELATIONS_PER_HOP"),
                "query-limit truncation must be detected");
        assertTrue(walkBody.contains("candidates.size() > 1"),
                "branches must mark the linear thread as truncated");

        int chainMethod = mapper.indexOf("listPublicChainByPostId(");
        assertTrue(chainMethod >= 0, "dedicated chain mapper query must exist");
        int chainSelect = mapper.lastIndexOf("@Select(\"\"\"", chainMethod);
        String chainSql = mapper.substring(chainSelect, chainMethod);
        int chainTypeFilter = chainSql.indexOf(
                "r.relation_type IN ('PREREQUISITE_OF', 'CONTINUES', 'SUPERSEDES')");
        int chainLimit = chainSql.indexOf("LIMIT #{limit}");
        assertTrue(chainTypeFilter >= 0 && chainLimit > chainTypeFilter,
                "the three ordered relation types must be filtered before LIMIT");
        assertFalse(chainSql.contains("'SUPPLEMENTS'")
                        || chainSql.contains("'CONTRADICTS'")
                        || chainSql.contains("'DUPLICATE_OF'"),
                "lateral relation types must never enter the bounded chain query");
        assertTrue(chainSql.contains("r.review_status = 'APPROVED'")
                        && chainSql.contains("r.visibility_status = 'VISIBLE'")
                        && chainSql.contains("source_post.visibility = 1")
                        && chainSql.contains("target_post.visibility = 1"),
                "chain SQL must preserve the public visibility contract");

        // Lateral relation types never join the chain.
        assertTrue(service.contains("PostKnowledgeRelationType.PREREQUISITE_OF")
                        && service.contains("PostKnowledgeRelationType.CONTINUES")
                        && service.contains("PostKnowledgeRelationType.SUPERSEDES"),
                "chain semantics must be limited to the three ordered types");
        int neighborStart = service.indexOf("private static Long chainNeighbor(");
        String neighborBody = service.substring(neighborStart, service.indexOf("private static PostKnowledgeRelationType parseType(", neighborStart));
        assertFalse(neighborBody.contains("SUPPLEMENTS") || neighborBody.contains("CONTRADICTS")
                        || neighborBody.contains("DUPLICATE_OF"),
                "lateral relation types must not appear in chain-neighbor selection");

        // Endpoint stays public + rate limited + read-only.
        assertTrue(controller.contains("@GetMapping(\"/thread\")"), "thread endpoint must exist");
        int threadStart = controller.indexOf("@GetMapping(\"/thread\")");
        int signature = controller.indexOf("readingThread(", threadStart);
        assertTrue(signature > threadStart, "thread endpoint must declare readingThread");
        // Annotations may sit on either side of @GetMapping, so scan the whole decorated block.
        String annotations = controller.substring(Math.max(0, threadStart - 300), signature);
        assertTrue(annotations.contains("@PublicApi"), "thread endpoint must be public");
        assertTrue(annotations.contains("@RateLimit"), "thread endpoint must be rate limited");
        assertTrue(annotations.contains("'public:post:knowledge-thread:' + #postId"),
                "thread rate limit must be keyed per post");
        assertTrue(annotations.contains("failOpen = false"),
                "thread rate limit must fail closed");
        String threadBody = controller.substring(signature, controller.indexOf("}", signature));
        assertFalse(threadBody.contains("PostMapping") || threadBody.contains("service.propose"),
                "thread endpoint must stay read-only");
    }
}
