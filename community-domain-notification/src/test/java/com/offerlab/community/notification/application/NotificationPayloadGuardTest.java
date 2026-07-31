package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPayloadGuardTest {

    @Test
    void curationFeedbackPayloadMustSurviveNotificationSanitizer() throws Exception {
        String controller = read("src/main/java/com/offerlab/community/notification/controller/NotificationController.java");
        String listener = read("src/main/java/com/offerlab/community/notification/application/NotificationEventListener.java");
        String consumer = read("src/main/java/com/offerlab/community/notification/application/TrustedContentNotificationConsumer.java");
        String resolver = read("../community-infrastructure/src/main/java/com/offerlab/community/infra/mq/producer/EventTopicResolver.java");
        String answerAcceptedEvent = read("../community-domain-interaction/src/main/java/com/offerlab/community/interaction/api/event/AnswerAcceptedEvent.java");

        assertContains(listener, "operationCurationSelectedContent");
        assertContains(listener, "content.put(\"eventId\"");
        assertContains(listener, "content.put(\"contentId\"");
        assertContains(listener, "content.put(\"contentTitle\"");
        assertContains(listener, "content.put(\"placementLabel\"");
        assertContains(listener, "content.put(\"sectionKey\"");
        assertContains(listener, "content.put(\"reasonText\"");

        assertContains(controller, "\"eventId\"");
        assertContains(controller, "\"contentId\"");
        assertContains(controller, "\"contentTitle\"");
        assertContains(controller, "\"placementId\"");
        assertContains(controller, "\"placementLabel\"");
        assertContains(controller, "\"sectionKey\"");
        assertContains(controller, "\"reasonText\"");
        assertContains(controller, "\"entrance\"");

        assertContains(listener, "answerAccepted");
        assertContains(listener, "contentSuggestionSubmitted");
        assertContains(listener, "contentSuggestionDecided");
        assertContains(listener, "content.put(\"postId\"");
        assertContains(listener, "content.put(\"commentId\"");
        assertContains(listener, "content.put(\"suggestionId\"");
        assertContains(listener, "content.put(\"decision\"");
        assertContains(listener, "\"#comment-\" + event.getCommentId()");
        assertContains(listener, "\"#content-suggestion-\" + event.getSuggestionId()");
        assertContains(answerAcceptedEvent, "private Long acceptanceId");
        assertContains(listener, "event.getAcceptanceId()");
        assertContains(listener, "ACTION_ANSWER_ACCEPTED + \":\" + event.getPostId()");
        assertContains(listener, "event.getCommentId() + \":\" + event.getAcceptanceId()");
        assertContains(listener, "NotificationDedupKey");
        assertContains(controller, "\"postId\"");
        assertContains(controller, "\"commentId\"");
        assertContains(controller, "\"suggestionId\"");
        assertContains(controller, "\"decision\"");

        for (String topic : new String[] {
                "interaction.answer.accepted",
                "interaction.content-suggestion.submitted",
                "interaction.content-suggestion.decided"
        }) {
            assertContains(consumer, topic);
            assertContains(resolver, topic);
        }
        assertContains(consumer,
                "groupId = \"${offerlab.notification.kafka-consumer-group:offerlab-notification-trusted-content}\"");
        assertContains(consumer, "containerFactory = \"kafkaListenerContainerFactory\"");
        assertContains(consumer, "autoStartup = \"${offerlab.notification.kafka-consumer-enabled:true}\"");
        assertContains(consumer, "EventEnvelope<?> envelope, Acknowledgment ack");
        assertContains(consumer, "objectMapper.convertValue");
        assertContains(consumer, "ack.acknowledge()");
        assertContains(consumer, "throw e");
        for (String handler : new String[] {
                "handleAnswerAcceptedSynchronously",
                "handleContentSuggestionSubmittedSynchronously",
                "handleContentSuggestionDecidedSynchronously"
        }) {
            assertContains(listener, handler);
            assertContains(consumer, handler);
        }
        assertFalse(consumer.contains("@Async"), "Kafka notification processing must stay synchronous until ack");
        assertFalse(consumer.contains(".onAnswerAccepted("), "Kafka must not call the asynchronous local listener");
        assertFalse(consumer.contains(".onContentSuggestionSubmitted("), "Kafka must not call the asynchronous local listener");
        assertFalse(consumer.contains(".onContentSuggestionDecided("), "Kafka must not call the asynchronous local listener");
        assertFalse(consumer.contains("runQuietly"), "Kafka failures must propagate to retry and DLT handling");

        TrustedContentNotificationConsumer runtimeConsumer = new TrustedContentNotificationConsumer(
                new NotificationEventListener(null, null, null, null, null, null),
                new ObjectMapper());
        EventEnvelope<?>[] incompleteMessages = {
                EventEnvelope.<Map<String, Object>>builder()
                        .eventType("ANSWER_ACCEPTED")
                        .payload(Map.of("postId", 11L, "commentId", 12L, "postAuthorUid", 13L))
                        .build(),
                EventEnvelope.<Map<String, Object>>builder()
                        .eventType("CONTENT_SUGGESTION_SUBMITTED")
                        .payload(Map.of("suggestionId", 21L, "postId", 22L))
                        .build(),
                EventEnvelope.<Map<String, Object>>builder()
                        .eventType("CONTENT_SUGGESTION_DECIDED")
                        .payload(Map.of("suggestionId", 31L, "postId", 32L, "submitterUid", 33L))
                        .build()
        };
        for (EventEnvelope<?> incompleteMessage : incompleteMessages) {
            AtomicBoolean acknowledged = new AtomicBoolean();
            Acknowledgment acknowledgment = () -> acknowledged.set(true);

            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> runtimeConsumer.onMessage(incompleteMessage, acknowledgment));

            assertTrue(failure.getMessage().contains("incomplete trusted-content notification payload"));
            assertFalse(acknowledged.get(), "Incomplete trusted-content messages must remain unacked for retry");
        }

        AtomicBoolean unknownAcknowledged = new AtomicBoolean();
        IllegalArgumentException unsupported = assertThrows(
                IllegalArgumentException.class,
                () -> runtimeConsumer.onMessage(
                        EventEnvelope.builder().eventType("UNKNOWN_TRUSTED_CONTENT_EVENT").build(),
                        () -> unknownAcknowledged.set(true)));
        assertTrue(unsupported.getMessage().contains("unsupported trusted-content notification event type"));
        assertFalse(unknownAcknowledged.get(), "Unsupported trusted-content events must remain unacked");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue(source.contains(expected), "Expected source to contain: " + expected);
    }
}
