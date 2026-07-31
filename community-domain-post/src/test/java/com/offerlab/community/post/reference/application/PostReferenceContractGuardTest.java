package com.offerlab.community.post.reference.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostReferenceContractGuardTest {

    @Test
    void controllerAndSqlKeepPublicReadsAndMutationsGuarded() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/post/reference/controller/PostReferenceController.java");
        String mapper = read("src/main/java/com/offerlab/community/post/reference/infrastructure/persistence/PostReferenceMapper.java");
        String service = read("src/main/java/com/offerlab/community/post/reference/application/PostReferenceService.java");

        assertTrue(controller.contains("@RequestMapping(\"/api/v1/posts/{postId}/references\")"));
        assertTrue(controller.contains("@GetMapping"));
        assertTrue(controller.contains("@PostMapping"));
        assertTrue(controller.contains("@PutMapping(\"/{referenceId}\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/{referenceId}\")"));
        assertTrue(controller.contains("@PutMapping(\"/reorder\")"));
        assertTrue(controller.contains("@PublicApi"));
        assertEquals(5, count(controller, "@RateLimit"));
        assertTrue(controller.contains("UserContext.require()"));

        assertTrue(mapper.contains("owner_uid = #{row.ownerUid}"));
        assertTrue(mapper.contains("revision = #{expectedRevision}"));
        assertTrue(mapper.contains("revision = revision + 1"));
        assertTrue(count(mapper, "is_deleted = 0") >= 9);
        assertTrue(mapper.contains("post_status = 1"));
        assertTrue(mapper.contains("visibility = 1"));

        assertTrue(service.contains("ExternalUrlSafety.requireSafeHttpUrl"));
        assertTrue(service.contains("toASCIIString()"));
        assertTrue(service.contains("requirePublicPost(postId);"));
        assertEquals(2, count(service, "requirePublicPost(postId);"));
        assertTrue(service.contains("brokenReason is required for BROKEN references"));
        assertTrue(service.contains("cleanBrokenReason = null"));
    }

    private static int count(String text, String fragment) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(fragment, offset)) >= 0) {
            count++;
            offset += fragment.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
