package com.offerlab.community.interaction.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentModerationEventPublicationGuardTest {

    @Test
    void approvedModeratedCommentUsesTransactionalOutboxPublisher() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/offerlab/community/interaction/application/"
                        + "CommentModerationHitQueueActionHandler.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("eventPublisher.publish(CommentCreatedEvent.builder()"));
        assertFalse(source.contains("ApplicationEventPublisher"));
        assertFalse(source.contains("events.publishEvent(CommentCreatedEvent.builder()"));
    }
}
