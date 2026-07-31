package com.offerlab.community.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.interaction.api.DiscussionFollowFacade;
import com.offerlab.community.interaction.api.event.CommentCreatedEvent;
import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.notification.infrastructure.persistence.mapper.SubscriptionUpdateDigestMapper;
import com.offerlab.community.notification.infrastructure.persistence.po.SubscriptionUpdateDigestPO;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.collaboration.api.CollaborationNeedFollowFacade;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.UserSubscriptionPreferenceFacade;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionUpdateDeliveryServiceTest {

    @Test
    void routesImmediateDigestMutedAndGlobalDisabledWithoutCrossDelivery() {
        RecordingNotificationFacade notifications = new RecordingNotificationFacade();
        List<SubscriptionUpdateDigestPO> digests = new ArrayList<>();
        SubscriptionUpdateDeliveryService service = deliveryService(
                (receiverUids, sourceType, sourceId) -> Map.of(
                        11L, preference("IMMEDIATE"),
                        12L, preference("DIGEST"),
                        13L, preference("MUTED"),
                        14L, preference("IMMEDIATE")),
                uid -> !Long.valueOf(14L).equals(uid),
                notifications.facade,
                digestService(digests, true));

        List<SubscriptionUpdateDeliveryResult> results = service.deliverForSource(List.of(
                topicCommand(11L, 100L),
                topicCommand(12L, 100L),
                topicCommand(13L, 100L),
                topicCommand(14L, 100L)));

        assertEquals(List.of(
                        SubscriptionUpdateDeliveryResult.Status.DELIVERED_IMMEDIATE,
                        SubscriptionUpdateDeliveryResult.Status.DELIVERED_DIGEST,
                        SubscriptionUpdateDeliveryResult.Status.SUPPRESSED_MUTED,
                        SubscriptionUpdateDeliveryResult.Status.SUPPRESSED_GLOBAL),
                results.stream().map(SubscriptionUpdateDeliveryResult::status).toList());
        assertEquals(List.of(11L), notifications.systemReceivers());
        assertEquals(1, digests.size());
        assertEquals(12L, digests.get(0).getReceiverUid());
        assertEquals("TOPIC", digests.get(0).getSourceType());
        assertEquals(100L, digests.get(0).getSourceId());
    }

    @Test
    void unavailableDigestStorageFailsClosedInsteadOfWritingAnImmediateNotification() {
        RecordingNotificationFacade notifications = new RecordingNotificationFacade();
        SubscriptionUpdateDeliveryService service = deliveryService(
                (receiverUids, sourceType, sourceId) -> Map.of(12L, preference("DIGEST")),
                uid -> true,
                notifications.facade,
                digestService(new ArrayList<>(), false));

        SubscriptionUpdateDeliveryResult result =
                service.deliverForSource(List.of(topicCommand(12L, 100L))).get(0);

        assertEquals(SubscriptionUpdateDeliveryResult.Status.FAILED, result.status());
        assertTrue(result.failed());
        assertTrue(notifications.systemReceivers().isEmpty());
    }

    @Test
    void topicFanoutRoutesMixedTopicModesPerSourceBeforeReceiverAggregation() {
        RecordingNotificationFacade notifications = new RecordingNotificationFacade();
        List<SubscriptionUpdateDigestPO> digests = new ArrayList<>();
        UserSubscriptionPreferenceFacade preferences = preferenceFacade((receiverUids, sourceType, sourceId) -> {
            if (Long.valueOf(10L).equals(sourceId)) {
                return Map.of(77L, preference("DIGEST"));
            }
            if (Long.valueOf(20L).equals(sourceId)) {
                return Map.of(77L, preference("IMMEDIATE"));
            }
            return Map.of();
        });
        UserFacade users = userFacade(uid -> true, Map.of());
        SubscriptionUpdateDeliveryService delivery = new SubscriptionUpdateDeliveryService(
                preferences, users, notifications.facade, digestService(digests, true));
        delivery.setFeatureFlagsForTest(true, true, true, true);
        NotificationEventListener listener = new NotificationEventListener(
                notifications.facade,
                users,
                emptyDiscussionFollows(),
                emptyNeedFollows(),
                new NotificationRetryService(null, null, null, null),
                delivery);
        PostPublishedEvent event = PostPublishedEvent.builder()
                .postId(501L)
                .authorId(9L)
                .title("Public update")
                .visibility(1)
                .postStatus(1)
                .timestamp(1_722_419_200_000L)
                .topicNotificationTargets(List.of(
                        topicTarget(10L, "system-design", 77L),
                        topicTarget(20L, "java", 77L)))
                .build();

        listener.handlePostPublishedSynchronously(event);

        assertEquals(List.of(77L), notifications.systemReceivers());
        assertEquals(20L, notifications.systemContents().get(0).get("topicId"));
        assertEquals(1, digests.size());
        assertEquals(77L, digests.get(0).getReceiverUid());
        assertEquals(10L, digests.get(0).getSourceId());
        assertEquals("TOPIC_POST_PUBLISHED", digests.get(0).getEventType());
    }

    @Test
    void sourceDeliveryPauseDoesNotSuppressDirectReplyOrMention() {
        RecordingNotificationFacade notifications = new RecordingNotificationFacade();
        UserFacade users = userFacade(uid -> true, Map.of("mentioned-user", 88L));
        SubscriptionUpdateDeliveryService delivery = new SubscriptionUpdateDeliveryService(
                preferenceFacade((receiverUids, sourceType, sourceId) -> Map.of()),
                users,
                notifications.facade,
                digestService(new ArrayList<>(), true));
        delivery.setFeatureFlagsForTest(false, false, false, false);
        DiscussionFollowFacade followers = discussionFollows(
                PageResult.of(List.of(66L), null, false));
        NotificationEventListener listener = new NotificationEventListener(
                notifications.facade,
                users,
                followers,
                emptyNeedFollows(),
                new NotificationRetryService(null, null, null, null),
                delivery);

        listener.handleCommentCreatedSynchronously(CommentCreatedEvent.builder()
                .uid(7L)
                .postId(501L)
                .postAuthorId(8L)
                .commentId(901L)
                .replyToUid(9L)
                .content("@mentioned-user hello")
                .timestamp(1_722_419_200_000L)
                .build());

        assertEquals(List.of(8L, 9L), notifications.commentReceivers());
        assertEquals(List.of(88L), notifications.mentionReceivers());
        assertFalse(notifications.commentReceivers().contains(66L));
    }

    @Test
    void discussionDigestMarksOnlyReceiversWithARealDeliveryFact() {
        RecordingNotificationFacade notifications = new RecordingNotificationFacade();
        List<SubscriptionUpdateDigestPO> digests = new ArrayList<>();
        List<Long> markedReceivers = new ArrayList<>();
        UserFacade users = userFacade(uid -> true, Map.of());
        SubscriptionUpdateDeliveryService delivery = new SubscriptionUpdateDeliveryService(
                preferenceFacade((receiverUids, sourceType, sourceId) -> Map.of(
                        66L, preference("DIGEST"),
                        67L, preference("MUTED"))),
                users,
                notifications.facade,
                digestService(digests, true));
        delivery.setFeatureFlagsForTest(true, true, true, true);
        NotificationEventListener listener = new NotificationEventListener(
                notifications.facade,
                users,
                discussionFollows(PageResult.of(List.of(66L, 67L), null, false), markedReceivers),
                emptyNeedFollows(),
                new NotificationRetryService(null, null, null, null),
                delivery);

        listener.handleCommentCreatedSynchronously(CommentCreatedEvent.builder()
                .uid(7L)
                .postId(501L)
                .postAuthorId(8L)
                .commentId(901L)
                .content("A public comment")
                .timestamp(1_722_419_200_000L)
                .build());

        assertEquals(List.of(66L), markedReceivers);
        assertTrue(notifications.discussionFollowerReceivers().isEmpty());
        assertEquals(1, digests.size());
        assertEquals(66L, digests.get(0).getReceiverUid());
        assertEquals("DISCUSSION", digests.get(0).getSourceType());
    }

    @Test
    void digestWriteFailureQueuesTheResolvedDigestRouteWithoutImmediateFallback() {
        RecordingNotificationFacade notifications = new RecordingNotificationFacade();
        RecordingSubscriptionRetryService retry = new RecordingSubscriptionRetryService();
        UserFacade users = userFacade(uid -> true, Map.of());
        SubscriptionUpdateDeliveryService delivery = new SubscriptionUpdateDeliveryService(
                preferenceFacade((receiverUids, sourceType, sourceId) -> Map.of(77L, preference("DIGEST"))),
                users,
                notifications.facade,
                digestService(new ArrayList<>(), false));
        delivery.setFeatureFlagsForTest(true, true, true, true);
        NotificationEventListener listener = new NotificationEventListener(
                notifications.facade,
                users,
                emptyDiscussionFollows(),
                emptyNeedFollows(),
                retry,
                delivery);

        listener.handlePostPublishedSynchronously(PostPublishedEvent.builder()
                .postId(501L)
                .authorId(9L)
                .visibility(1)
                .postStatus(1)
                .timestamp(1_722_419_200_000L)
                .topicNotificationTargets(List.of(topicTarget(10L, "system-design", 77L)))
                .build());

        assertTrue(notifications.systemReceivers().isEmpty());
        assertEquals(1, retry.commands.size());
        assertEquals(SubscriptionUpdateDeliveryMode.DIGEST, retry.modes.get(0));
        assertEquals("TOPIC", retry.commands.get(0).sourceType());
        assertEquals("topic_post_published:501", retry.commands.get(0).eventKey());
    }

    private static SubscriptionUpdateDeliveryService deliveryService(
            PreferenceLookup preferenceLookup,
            GlobalSystemPermission systemPermission,
            NotificationFacade notifications,
            SubscriptionUpdateDigestService digestService) {
        SubscriptionUpdateDeliveryService service = new SubscriptionUpdateDeliveryService(
                preferenceFacade(preferenceLookup),
                userFacade(systemPermission, Map.of()),
                notifications,
                digestService);
        service.setFeatureFlagsForTest(true, true, true, true);
        return service;
    }

    private static UserSubscriptionPreferenceFacade preferenceFacade(PreferenceLookup lookup) {
        return (UserSubscriptionPreferenceFacade) Proxy.newProxyInstance(
                UserSubscriptionPreferenceFacade.class.getClassLoader(),
                new Class<?>[]{UserSubscriptionPreferenceFacade.class},
                (proxy, method, args) -> {
                    if ("findEffectiveForRecipients".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        Collection<Long> receiverUids = (Collection<Long>) args[0];
                        return lookup.find(receiverUids, (String) args[1], (Long) args[2]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static UserFacade userFacade(
            GlobalSystemPermission systemPermission,
            Map<String, Long> mentions) {
        return (UserFacade) Proxy.newProxyInstance(
                UserFacade.class.getClassLoader(),
                new Class<?>[]{UserFacade.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "allowsSystemNotification" -> systemPermission.allows((Long) args[0]);
                    case "allowsCommentNotification", "allowsMentionNotification" -> true;
                    case "findUserIdsByNicknames" -> mentions;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static SubscriptionUpdateDigestService digestService(
            List<SubscriptionUpdateDigestPO> rows,
            boolean ready) {
        AtomicInteger ids = new AtomicInteger();
        SubscriptionUpdateDigestMapper mapper = (SubscriptionUpdateDigestMapper) Proxy.newProxyInstance(
                SubscriptionUpdateDigestMapper.class.getClassLoader(),
                new Class<?>[]{SubscriptionUpdateDigestMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tableExists" -> ready ? 1 : 0;
                    case "requiredColumnCount" -> ready ? 14 : 0;
                    case "insertIgnore" -> {
                        rows.add((SubscriptionUpdateDigestPO) args[0]);
                        yield 1;
                    }
                    default -> defaultValue(method.getReturnType());
                });
        return new SubscriptionUpdateDigestService(
                mapper,
                new SnowflakeIdGenerator() {
                    @Override
                    public synchronized long nextId() {
                        return ids.incrementAndGet();
                    }
                },
                new ObjectMapper());
    }

    private static SubscriptionUpdateDeliveryCommand topicCommand(Long receiverUid, Long sourceId) {
        return new SubscriptionUpdateDeliveryCommand(
                receiverUid,
                "TOPIC",
                sourceId,
                "POST",
                501L,
                "TOPIC_POST_PUBLISHED",
                "topic-post:501",
                9L,
                SubscriptionUpdateNotificationKind.SYSTEM,
                1,
                501L,
                Map.of(
                        "action", "topic_post_published",
                        "postId", 501L,
                        "topicId", sourceId,
                        "targetPath", "/post/501",
                        "summary", "A public topic update"),
                Instant.parse("2026-07-31T08:00:00Z"));
    }

    private static PostPublishedEvent.TopicNotificationTarget topicTarget(
            Long topicId, String slug, Long receiverUid) {
        return PostPublishedEvent.TopicNotificationTarget.builder()
                .topicId(topicId)
                .topicSlug(slug)
                .topicName(slug)
                .followerUids(List.of(receiverUid))
                .build();
    }

    private static DiscussionFollowFacade emptyDiscussionFollows() {
        return discussionFollows(PageResult.empty());
    }

    private static DiscussionFollowFacade discussionFollows(PageResult<Long> page) {
        return discussionFollows(page, new ArrayList<>());
    }

    private static DiscussionFollowFacade discussionFollows(
            PageResult<Long> page,
            List<Long> markedReceivers) {
        return (DiscussionFollowFacade) Proxy.newProxyInstance(
                DiscussionFollowFacade.class.getClassLoader(),
                new Class<?>[]{DiscussionFollowFacade.class},
                (proxy, method, args) -> {
                    if ("followerUidsForNotification".equals(method.getName()) && args.length == 4) {
                        return page;
                    }
                    if ("markNotified".equals(method.getName())) {
                        markedReceivers.add((Long) args[1]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static CollaborationNeedFollowFacade emptyNeedFollows() {
        return (needId, cursor, size) -> PageResult.empty();
    }

    private static UserSubscriptionPreferenceDTO preference(String deliveryMode) {
        return UserSubscriptionPreferenceDTO.builder()
                .deliveryMode(deliveryMode)
                .deliveryPreferenceSupported(true)
                .build();
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @FunctionalInterface
    private interface PreferenceLookup {
        Map<Long, UserSubscriptionPreferenceDTO> find(
                Collection<Long> receiverUids, String sourceType, Long sourceId);
    }

    @FunctionalInterface
    private interface GlobalSystemPermission {
        boolean allows(Long uid);
    }

    private static final class RecordingNotificationFacade {
        private final List<Long> systemReceivers = new ArrayList<>();
        private final List<Map<String, Object>> systemContents = new ArrayList<>();
        private final List<Long> commentReceivers = new ArrayList<>();
        private final List<Long> mentionReceivers = new ArrayList<>();
        private final List<Long> discussionFollowerReceivers = new ArrayList<>();
        private final NotificationFacade facade = (NotificationFacade) Proxy.newProxyInstance(
                NotificationFacade.class.getClassLoader(),
                new Class<?>[]{NotificationFacade.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "notifySystem" -> {
                            systemReceivers.add((Long) args[0]);
                            @SuppressWarnings("unchecked")
                            Map<String, Object> content = (Map<String, Object>) args[3];
                            systemContents.add(new LinkedHashMap<>(content));
                        }
                        case "notifyComment" -> commentReceivers.add((Long) args[0]);
                        case "notifyMention" -> mentionReceivers.add((Long) args[0]);
                        case "notifyDiscussionFollowComment", "notifyDiscussionFollowQualityComment" ->
                                discussionFollowerReceivers.add((Long) args[0]);
                        default -> {
                            // The test only needs the explicit notification surface above.
                        }
                    }
                    return defaultValue(method.getReturnType());
                });

        private List<Long> systemReceivers() {
            return List.copyOf(systemReceivers);
        }

        private List<Map<String, Object>> systemContents() {
            return List.copyOf(systemContents);
        }

        private List<Long> commentReceivers() {
            return List.copyOf(commentReceivers);
        }

        private List<Long> mentionReceivers() {
            return List.copyOf(mentionReceivers);
        }

        private List<Long> discussionFollowerReceivers() {
            return List.copyOf(discussionFollowerReceivers);
        }
    }

    private static final class RecordingSubscriptionRetryService extends NotificationRetryService {
        private final List<SubscriptionUpdateDeliveryCommand> commands = new ArrayList<>();
        private final List<SubscriptionUpdateDeliveryMode> modes = new ArrayList<>();

        private RecordingSubscriptionRetryService() {
            super(null, null, null, null);
        }

        @Override
        boolean enqueueSubscriptionUpdateDelivery(SubscriptionUpdateDeliveryCommand command,
                                                  SubscriptionUpdateDeliveryMode deliveryMode,
                                                  Throwable cause) {
            commands.add(command);
            modes.add(deliveryMode);
            return true;
        }
    }
}
