package com.offerlab.community.feed.application;

import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.infra.mq.idempotent.IdempotentChecker;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.FollowCursorDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedFanoutRetryTest {

    @Test
    void partialInboxFailureReleasesIdempotencyAndRetryReplaysAllFollowers() {
        RecordingIdempotentChecker checker = new RecordingIdempotentChecker();
        RecordingFeedInboxRedis feedRedis = new RecordingFeedInboxRedis(102L);
        FeedFanoutService service = new FeedFanoutService(
                followerFacade(List.of(follower(1L, 101L), follower(2L, 102L), follower(3L, 103L))),
                feedRedis,
                checker
        );

        assertThrows(IllegalStateException.class,
                () -> service.fanoutPostPublished(event(), "kafka:first"));

        assertEquals(1, checker.releaseCount);
        assertEquals(List.of(101L, 102L), feedRedis.inboxAttempts);

        assertTrue(service.fanoutPostPublished(event(), "kafka:retry"));

        assertEquals(List.of(101L, 102L, 101L, 102L, 103L), feedRedis.inboxAttempts);
        assertEquals(2, feedRedis.authorTimelineWrites);
        assertEquals(2, feedRedis.globalLatestWrites);
    }

    private static FollowCursorDTO follower(Long relationId, Long uid) {
        return FollowCursorDTO.builder().relationId(relationId).uid(uid).build();
    }

    private static PostPublishedEvent event() {
        return PostPublishedEvent.builder()
                .postId(88L)
                .authorId(7L)
                .visibility(Post.VIS_PUBLIC)
                .postStatus(Post.STATUS_PUBLISHED)
                .timestamp(123456L)
                .build();
    }

    private static UserFacade followerFacade(List<FollowCursorDTO> followers) {
        return (UserFacade) Proxy.newProxyInstance(
                UserFacade.class.getClassLoader(),
                new Class<?>[]{UserFacade.class},
                (proxy, method, args) -> {
                    if ("getFollowerPage".equals(method.getName())) {
                        return ((Long) args[1]) == 0L ? followers : List.of();
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return 0;
    }

    private static final class RecordingIdempotentChecker extends IdempotentChecker {

        private boolean consumed;
        private int releaseCount;

        private RecordingIdempotentChecker() {
            super(null);
        }

        @Override
        public boolean tryConsume(String messageId, String consumerName) {
            if (consumed) {
                return false;
            }
            consumed = true;
            return true;
        }

        @Override
        public void release(String messageId, String consumerName) {
            consumed = false;
            releaseCount++;
        }
    }

    private static final class RecordingFeedInboxRedis extends FeedInboxRedis {

        private final Long failOnceUid;
        private final List<Long> inboxAttempts = new ArrayList<>();
        private boolean failed;
        private int authorTimelineWrites;
        private int globalLatestWrites;

        private RecordingFeedInboxRedis(Long failOnceUid) {
            super(null, null);
            this.failOnceUid = failOnceUid;
        }

        @Override
        public void addToInbox(Long uid, Long postId, long ts) {
            inboxAttempts.add(uid);
            if (!failed && failOnceUid.equals(uid)) {
                failed = true;
                throw new IllegalStateException("simulated Redis failure");
            }
        }

        @Override
        public void addToAuthorTimeline(Long authorUid, Long postId, long ts) {
            authorTimelineWrites++;
        }

        @Override
        public void addToGlobalLatest(Long postId, long ts) {
            globalLatestWrites++;
        }
    }
}
