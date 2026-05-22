package com.offerlab.community.feed.application;

import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.infra.mq.idempotent.IdempotentChecker;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.user.api.UserFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedFanoutService {

    private static final int MAX_FANOUT = 10000;
    private static final String CONSUMER_NAME = "feed-fanout";

    private final UserFacade userFacade;
    private final FeedInboxRedis feedRedis;
    private final IdempotentChecker idempotentChecker;

    public boolean fanoutPostPublished(PostPublishedEvent event, String source) {
        Long postId = event == null ? null : event.getPostId();
        Long authorId = event == null ? null : event.getAuthorId();
        if (postId == null || authorId == null) {
            log.warn("feed fanout skipped: invalid post published event source={} event={}", source, event);
            return true;
        }

        String idempotentKey = "post.published:" + postId;
        if (!idempotentChecker.tryConsume(idempotentKey, CONSUMER_NAME)) {
            log.info("feed fanout skipped duplicate: source={} postId={} authorId={}", source, postId, authorId);
            return false;
        }

        try {
            long ts = event.getTimestamp() == null ? System.currentTimeMillis() : event.getTimestamp();
            feedRedis.addToAuthorTimeline(authorId, postId, ts);
            feedRedis.addToGlobalLatest(postId, ts);

            List<Long> followers = userFacade.getFollowerIds(authorId, 0, MAX_FANOUT);
            for (Long fuid : followers) {
                feedRedis.addToInbox(fuid, postId, ts);
            }
            log.info("feed fanout done: source={} postId={} authorId={} followers={}",
                    source, postId, authorId, followers.size());
            return true;
        } catch (Exception e) {
            idempotentChecker.release(idempotentKey, CONSUMER_NAME);
            log.error("feed fanout failed: source={} postId={} authorId={}", source, postId, authorId, e);
            throw e;
        }
    }
}
