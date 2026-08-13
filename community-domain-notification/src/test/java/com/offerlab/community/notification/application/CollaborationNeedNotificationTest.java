package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.mq.EventEnvelope;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.notification.controller.NotificationController;
import com.offerlab.community.post.collaboration.api.CollaborationNeedFollowFacade;
import com.offerlab.community.post.collaboration.api.CollaborationNeedStateChangedEvent;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.UserSubscriptionPreferenceFacade;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborationNeedNotificationTest {

    private static final Long NEED_ID = 101L;
    private static final Long TARGET_NEED_ID = 202L;
    private static final Long ACTOR_UID = 99L;
    private static final Long CREATOR_UID = 10L;
    private static final Long CLAIMANT_UID = 20L;

    @Test
    void listenerUsesAfterCommitAsyncAndConsumerUsesTheSynchronousHandler() throws Exception {
        Method local = NotificationEventListener.class.getMethod(
                "onCollaborationNeedStateChanged", CollaborationNeedStateChangedEvent.class);
        Async async = local.getAnnotation(Async.class);
        TransactionalEventListener transactional = local.getAnnotation(TransactionalEventListener.class);
        assertNotNull(async);
        assertEquals("notificationAsyncExecutor", async.value());
        assertNotNull(transactional);
        assertEquals(TransactionPhase.AFTER_COMMIT, transactional.phase());

        Method synchronous = NotificationEventListener.class.getMethod(
                "handleCollaborationNeedStateChangedSynchronously",
                CollaborationNeedStateChangedEvent.class);
        assertTrue(Modifier.isPublic(synchronous.getModifiers()));
        assertNull(synchronous.getAnnotation(Async.class));

        Method consume = CollaborationNeedNotificationConsumer.class.getMethod(
                "onMessage", EventEnvelope.class, Acknowledgment.class);
        KafkaListener kafkaListener = consume.getAnnotation(KafkaListener.class);
        assertNotNull(kafkaListener);
        assertArrayEquals(new String[]{"collaboration.need.state-changed"}, kafkaListener.topics());
        assertEquals("kafkaListenerContainerFactory", kafkaListener.containerFactory());
    }

    @Test
    void routesDirectRecipientsByEventMatrixAndBuildsStablePayload() {
        Map<String, Set<Long>> matrix = new LinkedHashMap<>();
        matrix.put("CLAIMED", Set.of(CREATOR_UID));
        matrix.put("SUBMITTED", Set.of(CREATOR_UID));
        matrix.put("REJECTED", Set.of(CREATOR_UID, CLAIMANT_UID));
        matrix.put("WITHDRAWN", Set.of(CREATOR_UID));
        matrix.put("ACCEPTED", Set.of(CREATOR_UID, CLAIMANT_UID));
        matrix.put("COMPLETED", Set.of(CREATOR_UID, CLAIMANT_UID));
        matrix.put("CLOSED", Set.of(CREATOR_UID, CLAIMANT_UID));
        matrix.put("MERGED", Set.of(CREATOR_UID, CLAIMANT_UID));
        matrix.put("RELEASED", Set.of(CREATOR_UID));

        matrix.forEach((eventType, expectedReceivers) -> {
            RecordingNotificationFacade recording = new RecordingNotificationFacade(Set.of());
            NotificationEventListener listener = listener(
                    recording, emptyFollowers(), new RecordingRetryService());

            listener.handleCollaborationNeedStateChangedSynchronously(event(eventType));

            assertEquals(expectedReceivers, recording.receivers(), eventType);
            for (NotificationCall call : recording.calls) {
                assertNull(call.targetType(), eventType);
                assertEquals(NEED_ID, call.targetId(), eventType);
                assertEquals("collaboration_need_state_changed", call.content().get("action"), eventType);
                assertEquals(NEED_ID, call.content().get("needId"), eventType);
                assertEquals(eventType, call.content().get("eventType"), eventType);
                assertEquals(toStatus(eventType), call.content().get("status"), eventType);
                assertEquals("collaboration_need_event:9001", call.content().get("dedupKey"), eventType);
                assertEquals("private transition note", call.content().get("reasonText"), eventType);
                assertEquals(
                        "MERGED".equals(eventType)
                                ? "/collaboration/needs/" + TARGET_NEED_ID
                                : "/collaboration/needs/" + NEED_ID,
                        call.content().get("targetPath"),
                        eventType);
                if ("MERGED".equals(eventType)) {
                    assertEquals(TARGET_NEED_ID, call.content().get("targetNeedId"));
                }
            }
        });
    }

    @Test
    void excludesActorEmptyAndDuplicateDirectRecipients() {
        RecordingNotificationFacade recording = new RecordingNotificationFacade(Set.of());
        NotificationEventListener listener = listener(
                recording, emptyFollowers(), new RecordingRetryService());

        CollaborationNeedStateChangedEvent actorIsCreator = event("CLAIMED");
        actorIsCreator.setActorUid(CREATOR_UID);
        listener.handleCollaborationNeedStateChangedSynchronously(actorIsCreator);

        CollaborationNeedStateChangedEvent duplicateParticipants = event("MERGED");
        duplicateParticipants.setCreatorUid(CLAIMANT_UID);
        listener.handleCollaborationNeedStateChangedSynchronously(duplicateParticipants);

        assertEquals(List.of(CLAIMANT_UID), recording.calls.stream()
                .map(NotificationCall::receiverUid)
                .toList());
    }

    @Test
    void creatorActionsDoNotNotifyTheCreatorTwiceAndClaimantMayBeAbsentForDirectCompletion() {
        RecordingNotificationFacade recording = new RecordingNotificationFacade(Set.of());
        NotificationEventListener listener = listener(
                recording, emptyFollowers(), new RecordingRetryService());

        CollaborationNeedStateChangedEvent acceptedByCreator = event("ACCEPTED");
        acceptedByCreator.setActorUid(CREATOR_UID);
        listener.handleCollaborationNeedStateChangedSynchronously(acceptedByCreator);

        CollaborationNeedStateChangedEvent openCompletedByCreator = event("COMPLETED");
        openCompletedByCreator.setActorUid(CREATOR_UID);
        openCompletedByCreator.setClaimantUid(null);
        listener.handleCollaborationNeedStateChangedSynchronously(openCompletedByCreator);

        assertEquals(List.of(CLAIMANT_UID), recording.calls.stream()
                .map(NotificationCall::receiverUid)
                .toList());
    }

    @Test
    void paginatesFollowersWithoutLeakingNotesOrLoopingOnAStuckCursor() {
        List<Long> requestedCursors = new ArrayList<>();
        CollaborationNeedFollowFacade followers = (needId, cursor, size) -> {
            assertEquals(NEED_ID, needId);
            assertEquals(100, size);
            requestedCursors.add(cursor);
            if (cursor == 0L) {
                return PageResult.of(
                        Arrays.asList(CLAIMANT_UID, 30L, 30L, ACTOR_UID, null, 0L),
                        "50",
                        true);
            }
            return PageResult.of(List.of(40L, CLAIMANT_UID), "50", true);
        };
        RecordingNotificationFacade recording = new RecordingNotificationFacade(Set.of());
        NotificationEventListener listener = listener(
                recording, followers, new RecordingRetryService());

        listener.handleCollaborationNeedStateChangedSynchronously(event("COMPLETED"));

        assertEquals(List.of(0L, 50L), requestedCursors);
        assertEquals(Set.of(CREATOR_UID, CLAIMANT_UID, 30L, 40L), recording.receivers());
        assertEquals(1L, recording.calls.stream()
                .filter(call -> call.receiverUid().equals(CLAIMANT_UID))
                .count());
        NotificationCall direct = recording.callFor(CLAIMANT_UID);
        assertEquals("private transition note", direct.content().get("reasonText"));
        for (Long followerUid : List.of(30L, 40L)) {
            NotificationCall follower = recording.callFor(followerUid);
            assertFalse(follower.content().containsKey("reasonText"));
            assertEquals("collaboration_need_event:9001", follower.content().get("dedupKey"));
            assertEquals("/collaboration/needs/" + NEED_ID, follower.content().get("targetPath"));
        }
    }

    @Test
    void receiverFailureEnqueuesTheSameDedupPayloadAndDoesNotBlockOthers() {
        RecordingNotificationFacade recording = new RecordingNotificationFacade(Set.of(CREATOR_UID));
        RecordingRetryService retry = new RecordingRetryService();
        CollaborationNeedFollowFacade followers = (needId, cursor, size) ->
                PageResult.of(List.of(30L, 30L, CREATOR_UID, CLAIMANT_UID), null, false);
        NotificationEventListener listener = listener(recording, followers, retry);

        listener.handleCollaborationNeedStateChangedSynchronously(event("MERGED"));

        assertEquals(List.of(CREATOR_UID, CLAIMANT_UID, 30L), recording.calls.stream()
                .map(NotificationCall::receiverUid)
                .toList());
        assertEquals(1, retry.calls.size());
        RetryCall retryCall = retry.calls.get(0);
        assertEquals(CREATOR_UID, retryCall.receiverUid());
        assertEquals(0L, retryCall.senderUid());
        assertEquals(5, retryCall.notifType());
        assertNull(retryCall.targetType());
        assertEquals(NEED_ID, retryCall.targetId());
        assertEquals(recording.callFor(CREATOR_UID).content(), retryCall.content());
        assertEquals(
                NotificationDedupKey.of(
                        retryCall.receiverUid(),
                        retryCall.senderUid(),
                        retryCall.notifType(),
                        retryCall.targetType(),
                        retryCall.targetId(),
                        retryCall.content()),
                NotificationDedupKey.of(
                        CREATOR_UID,
                        0L,
                        5,
                        null,
                        NEED_ID,
                        recording.callFor(CREATOR_UID).content()));
        assertEquals(Set.of(CREATOR_UID, CLAIMANT_UID, 30L), recording.receivers());
    }

    @Test
    void kafkaDoesNotAckWhenNotificationAndRetryPersistenceBothFail() {
        RecordingNotificationFacade recording = new RecordingNotificationFacade(Set.of(CREATOR_UID));
        NotificationEventListener listener = listener(
                recording, emptyFollowers(), new RecordingRetryService(false));
        CollaborationNeedNotificationConsumer consumer = new CollaborationNeedNotificationConsumer(
                listener, new ObjectMapper());
        AtomicBoolean acknowledged = new AtomicBoolean();

        assertThrows(
                IllegalStateException.class,
                () -> consumer.onMessage(
                        EventEnvelope.builder()
                                .messageId("message-retry-unavailable")
                                .eventType("COLLABORATION_NEED_STATE_CHANGED")
                                .payload(event("CLAIMED"))
                                .build(),
                        () -> acknowledged.set(true)));

        assertFalse(acknowledged.get());
    }

    @Test
    void localAndKafkaPathsProduceIdenticalCallsAndKafkaValidatesBeforeAck() {
        ObjectMapper objectMapper = new ObjectMapper();
        CollaborationNeedStateChangedEvent event = event("CLAIMED");

        RecordingNotificationFacade localRecording = new RecordingNotificationFacade(Set.of());
        NotificationEventListener localListener = listener(
                localRecording, emptyFollowers(), new RecordingRetryService());
        localListener.onCollaborationNeedStateChanged(event);

        RecordingNotificationFacade kafkaRecording = new RecordingNotificationFacade(Set.of());
        NotificationEventListener kafkaListener = listener(
                kafkaRecording, emptyFollowers(), new RecordingRetryService());
        CollaborationNeedNotificationConsumer consumer = new CollaborationNeedNotificationConsumer(
                kafkaListener, objectMapper);
        AtomicBoolean acknowledged = new AtomicBoolean();
        consumer.onMessage(
                EventEnvelope.builder()
                        .messageId("message-1")
                        .eventType("COLLABORATION_NEED_STATE_CHANGED")
                        .payload(objectMapper.convertValue(event, Map.class))
                        .build(),
                () -> acknowledged.set(true));

        assertTrue(acknowledged.get());
        assertEquals(localRecording.calls, kafkaRecording.calls);

        Map<String, Object> incompletePayload = new HashMap<>(
                objectMapper.convertValue(event, Map.class));
        incompletePayload.remove("dedupKey");
        AtomicBoolean incompleteAcknowledged = new AtomicBoolean();
        IllegalArgumentException incomplete = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.onMessage(
                        EventEnvelope.builder()
                                .messageId("message-2")
                                .eventType("COLLABORATION_NEED_STATE_CHANGED")
                                .payload(incompletePayload)
                                .build(),
                        () -> incompleteAcknowledged.set(true)));
        assertTrue(incomplete.getMessage().contains("dedupKey"));
        assertFalse(incompleteAcknowledged.get());

        AtomicBoolean unsupportedAcknowledged = new AtomicBoolean();
        assertThrows(
                IllegalArgumentException.class,
                () -> consumer.onMessage(
                        EventEnvelope.builder()
                                .messageId("message-3")
                                .eventType("OTHER_EVENT")
                                .payload(Map.of())
                                .build(),
                        () -> unsupportedAcknowledged.set(true)));
        assertFalse(unsupportedAcknowledged.get());
    }

    @Test
    void localAndKafkaDeliveriesCollapseToOneLogicalNotificationByExplicitDedupKey() {
        ObjectMapper objectMapper = new ObjectMapper();
        CollaborationNeedStateChangedEvent event = event("CLAIMED");
        RecordingNotificationFacade recording = new RecordingNotificationFacade(Set.of(), true);
        NotificationEventListener listener = listener(
                recording, emptyFollowers(), new RecordingRetryService());
        CollaborationNeedNotificationConsumer consumer = new CollaborationNeedNotificationConsumer(
                listener, objectMapper);

        listener.onCollaborationNeedStateChanged(event);
        consumer.onMessage(
                EventEnvelope.builder()
                        .messageId("message-dedup")
                        .eventType("COLLABORATION_NEED_STATE_CHANGED")
                        .payload(objectMapper.convertValue(event, Map.class))
                        .build(),
                () -> {
                });

        assertEquals(1, recording.calls.size());
        assertEquals("collaboration_need_event:9001",
                recording.calls.get(0).content().get("dedupKey"));
    }

    @Test
    void validatesEveryRequiredEventFieldAndMergedTarget() {
        List<Consumer<CollaborationNeedStateChangedEvent>> invalidators = List.of(
                value -> value.setEventId(null),
                value -> value.setNeedId(null),
                value -> value.setEventType(null),
                value -> value.setActorUid(null),
                value -> value.setCreatorUid(null),
                value -> value.setDomain(null),
                value -> value.setFromStatus(null),
                value -> value.setToStatus(null),
                value -> value.setOccurredAt(null),
                value -> value.setDedupKey(null)
        );
        NotificationEventListener listener = listener(
                new RecordingNotificationFacade(Set.of()),
                emptyFollowers(),
                new RecordingRetryService());

        for (Consumer<CollaborationNeedStateChangedEvent> invalidator : invalidators) {
            CollaborationNeedStateChangedEvent invalid = event("CLAIMED");
            invalidator.accept(invalid);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> listener.handleCollaborationNeedStateChangedSynchronously(invalid));
        }

        CollaborationNeedStateChangedEvent missingClaimant = event("REJECTED");
        missingClaimant.setClaimantUid(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> listener.handleCollaborationNeedStateChangedSynchronously(missingClaimant));

        CollaborationNeedStateChangedEvent merged = event("MERGED");
        merged.setTargetNeedId(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> listener.handleCollaborationNeedStateChangedSynchronously(merged));
    }

    @Test
    @SuppressWarnings("unchecked")
    void notificationApiKeepsOnlySafeCollaborationFields() throws Exception {
        NotificationController controller = new NotificationController(null);
        Method sanitize = NotificationController.class.getDeclaredMethod("sanitizeContent", Map.class);
        sanitize.setAccessible(true);
        Map<String, Object> sanitized = (Map<String, Object>) sanitize.invoke(
                controller,
                Map.of(
                        "needId", NEED_ID,
                        "targetNeedId", TARGET_NEED_ID,
                        "reasonText", "safe direct note",
                        "note", "private unlisted note",
                        "internalFollowerUids", List.of(1L, 2L)
                ));

        assertEquals(NEED_ID, sanitized.get("needId"));
        assertEquals(TARGET_NEED_ID, sanitized.get("targetNeedId"));
        assertEquals("safe direct note", sanitized.get("reasonText"));
        assertFalse(sanitized.containsKey("note"));
        assertFalse(sanitized.containsKey("internalFollowerUids"));
    }

    private static NotificationEventListener listener(
            RecordingNotificationFacade recording,
            CollaborationNeedFollowFacade followers,
            RecordingRetryService retry) {
        return new NotificationEventListener(
                recording.facade,
                null,
                null,
                followers,
                retry,
                followerDeliveryService(recording.facade));
    }

    private static SubscriptionUpdateDeliveryService followerDeliveryService(
            NotificationFacade notificationFacade) {
        UserSubscriptionPreferenceFacade preferences =
                (UserSubscriptionPreferenceFacade) Proxy.newProxyInstance(
                        UserSubscriptionPreferenceFacade.class.getClassLoader(),
                        new Class<?>[]{UserSubscriptionPreferenceFacade.class},
                        (proxy, method, args) -> "findEffectiveForRecipients".equals(method.getName())
                                ? Map.of()
                                : defaultValue(method.getReturnType()));
        UserFacade users = (UserFacade) Proxy.newProxyInstance(
                UserFacade.class.getClassLoader(),
                new Class<?>[]{UserFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "allowsSystemNotification", "allowsCommentNotification" -> true;
                    default -> defaultValue(method.getReturnType());
                });
        SubscriptionUpdateDeliveryService service = new SubscriptionUpdateDeliveryService(
                preferences, users, notificationFacade, null);
        service.setFeatureFlagsForTest(true, true, true, true);
        return service;
    }

    private static CollaborationNeedFollowFacade emptyFollowers() {
        return (needId, cursor, size) -> PageResult.empty();
    }

    private static CollaborationNeedStateChangedEvent event(String eventType) {
        return CollaborationNeedStateChangedEvent.builder()
                .eventId(9001L)
                .needId(NEED_ID)
                .eventType(eventType)
                .actorUid(ACTOR_UID)
                .creatorUid(CREATOR_UID)
                .claimantUid(CLAIMANT_UID)
                .domain(1)
                .targetNeedId("MERGED".equals(eventType) ? TARGET_NEED_ID : null)
                .targetType("POST")
                .targetId(303L)
                .note("private transition note")
                .fromStatus("PREVIOUS")
                .toStatus(toStatus(eventType))
                .occurredAt(1_721_260_800_000L)
                .dedupKey("upstream:collaboration:need:9001")
                .build();
    }

    private static String toStatus(String eventType) {
        return switch (eventType) {
            case "REJECTED", "WITHDRAWN" -> "CLAIMED";
            case "ACCEPTED", "COMPLETED" -> "COMPLETED";
            case "RELEASED" -> "OPEN";
            default -> eventType;
        };
    }

    private static final class RecordingNotificationFacade {
        private final List<NotificationCall> calls = new ArrayList<>();
        private final Set<Long> failingReceivers;
        private final boolean deduplicate;
        private final Set<String> dedupKeys = new HashSet<>();
        private final NotificationFacade facade;

        private RecordingNotificationFacade(Set<Long> failingReceivers) {
            this(failingReceivers, false);
        }

        private RecordingNotificationFacade(Set<Long> failingReceivers, boolean deduplicate) {
            this.failingReceivers = failingReceivers;
            this.deduplicate = deduplicate;
            this.facade = (NotificationFacade) Proxy.newProxyInstance(
                    NotificationFacade.class.getClassLoader(),
                    new Class<?>[]{NotificationFacade.class},
                    (proxy, method, args) -> {
                        if ("notifySystem".equals(method.getName())) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> content = new LinkedHashMap<>(
                                    (Map<String, Object>) args[3]);
                            Long receiverUid = (Long) args[0];
                            Long targetType = (Long) args[1];
                            Long targetId = (Long) args[2];
                            String dedupKey = NotificationDedupKey.of(
                                    receiverUid,
                                    0L,
                                    5,
                                    targetType == null ? null : targetType.intValue(),
                                    targetId,
                                    content);
                            if (deduplicate && !dedupKeys.add(dedupKey)) {
                                return null;
                            }
                            calls.add(new NotificationCall(
                                    receiverUid,
                                    targetType,
                                    targetId,
                                    content));
                            if (failingReceivers.contains(receiverUid)) {
                                throw new IllegalStateException("simulated notification write failure");
                            }
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private Set<Long> receivers() {
            return calls.stream()
                    .map(NotificationCall::receiverUid)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        private NotificationCall callFor(Long receiverUid) {
            return calls.stream()
                    .filter(call -> call.receiverUid().equals(receiverUid))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static final class RecordingRetryService extends NotificationRetryService {
        private final List<RetryCall> calls = new ArrayList<>();
        private final boolean persisted;

        private RecordingRetryService() {
            this(true);
        }

        private RecordingRetryService(boolean persisted) {
            super(null, null, null, null);
            this.persisted = persisted;
        }

        @Override
        public boolean enqueue(
                String scene,
                Long receiverUid,
                Long senderUid,
                Integer notifType,
                Integer targetType,
                Long targetId,
                Map<String, Object> content,
                Throwable cause) {
            calls.add(new RetryCall(
                    scene,
                    receiverUid,
                    senderUid,
                    notifType,
                    targetType,
                    targetId,
                    new LinkedHashMap<>(content),
                    cause));
            return persisted;
        }
    }

    private record NotificationCall(
            Long receiverUid,
            Long targetType,
            Long targetId,
            Map<String, Object> content) {
    }

    private record RetryCall(
            String scene,
            Long receiverUid,
            Long senderUid,
            Integer notifType,
            Integer targetType,
            Long targetId,
            Map<String, Object> content,
            Throwable cause) {
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
