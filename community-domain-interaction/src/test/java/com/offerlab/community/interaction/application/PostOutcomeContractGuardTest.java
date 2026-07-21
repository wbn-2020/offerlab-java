package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostOutcomeContractGuardTest {

    @Test
    void outcomeLifecycleMustStayPrivateReviewedAnonymousSafeAndRevisioned() throws Exception {
        String service = read("src/main/java/com/offerlab/community/interaction/application/PostOutcomeService.java");
        String mapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/PostOutcomeMapper.java");
        String controller = read("src/main/java/com/offerlab/community/interaction/controller/PostOutcomeController.java");
        String adminController = read("src/main/java/com/offerlab/community/interaction/controller/PostOutcomeAdminController.java");
        String revisitService = read("src/main/java/com/offerlab/community/interaction/application/UserRevisitService.java");
        String revisitMapper = read("src/main/java/com/offerlab/community/interaction/infrastructure/persistence/mapper/UserRevisitItemMapper.java");
        String event = read("src/main/java/com/offerlab/community/interaction/api/event/PostOutcomeChangedEvent.java");
        String topicResolver = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/mq/producer/EventTopicResolver.java");

        assertTrue(service.contains("cmd.getVisibility() == null")
                && service.contains("PostOutcomeVisibility.PRIVATE"));
        assertTrue(service.contains("PostOutcomePublicationStatus.PENDING_REVIEW"));
        assertTrue(service.contains("PostOutcomePublicationStatus.PRIVATE"));
        assertTrue(service.contains("MIN_PUBLIC_SAMPLE_SIZE = 3"));
        assertTrue(service.contains("requireNotAuthor(uid, post)"));
        assertTrue(service.contains("outcome contributors and post authors cannot review this outcome"));
        assertTrue(service.contains("postFacade.getPostMetadata"));
        assertFalse(service.contains("PostRepository"));

        assertTrue(mapper.contains("publication_status = 'PUBLISHED'"));
        assertTrue(mapper.contains("uid <> #{postAuthorUid}"));
        assertTrue(mapper.contains("revision = revision + 1"));
        assertTrue(mapper.contains("revision = #{expectedRevision}"));
        assertTrue(count(mapper, "is_deleted = 0") >= 10);

        assertTrue(controller.contains("@GetMapping(\"/summary\")"));
        assertTrue(controller.contains("@GetMapping(\"/mine\")"));
        assertTrue(controller.contains("@PutMapping(\"/mine\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/mine\")"));
        assertTrue(count(controller, "@RateLimit") == 4);
        assertTrue(adminController.contains("@PutMapping(\"/{outcomeId}/review\")"));
        assertTrue(adminController.contains("@RateLimit"));

        assertTrue(revisitService.contains("schedulePostOutcome"));
        assertTrue(revisitService.contains("cancelPostOutcome"));
        assertTrue(revisitMapper.contains("'POST_OUTCOME'"));
        assertTrue(revisitMapper.contains("completePostOutcome"));

        assertTrue(event.contains("outcomeRevision"));
        assertTrue(topicResolver.contains("\"POST_OUTCOME_CHANGED\""));
        assertTrue(topicResolver.contains("\"POST_OUTCOME\""));
    }

    private static int count(String source, String token) {
        return source.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }
}
