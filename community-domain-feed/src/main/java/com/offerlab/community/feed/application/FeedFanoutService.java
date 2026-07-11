package com.offerlab.community.feed.application;

import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.infra.mq.idempotent.IdempotentChecker;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.FollowCursorDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedFanoutService {

    private static final int FANOUT_BATCH_SIZE = 1000;
    private static final String CONSUMER_NAME = "feed-fanout";

    private final UserFacade userFacade;
    private final FeedInboxRedis feedRedis;
    private final IdempotentChecker idempotentChecker;

    public boolean fanoutPostPublished(PostPublishedEvent event, String source) {
        Long postId = event == null ? null : event.getPostId();
        Long authorId = event == null ? null : event.getAuthorId();
        if (postId == null || authorId == null) {
            log.warn("feed fanout skipped: invalid post published event source={}", source);
            return true;
        }
        if (!isPublicPublished(event)) {
            log.warn("feed fanout skipped: non-public post source={} postId={} authorId={} visibility={} status={}",
                    source, LogMask.id(postId), LogMask.id(authorId), event.getVisibility(), event.getPostStatus());
            return true;
        }

        String idempotentKey = "post.published:" + postId;
        if (!idempotentChecker.tryConsume(idempotentKey, CONSUMER_NAME)) {
            log.info("feed fanout skipped duplicate: source={} postId={} authorId={}",
                    source, LogMask.id(postId), LogMask.id(authorId));
            return false;
        }

        long followerCount = 0L;
        int failedWrites = 0;
        try {
            long ts = event.getTimestamp() == null ? System.currentTimeMillis() : event.getTimestamp();
            feedRedis.addToAuthorTimeline(authorId, postId, ts);
            feedRedis.addToGlobalLatest(postId, ts);

            long cursor = 0L;
            int batches = 0;
            while (true) {
                List<FollowCursorDTO> followers = userFacade.getFollowerPage(authorId, cursor, FANOUT_BATCH_SIZE);
                if (followers == null || followers.isEmpty()) {
                    break;
                }
                batches++;
                for (FollowCursorDTO follower : followers) {
                    if (follower == null || follower.getUid() == null) {
                        continue;
                    }
                    try {
                        feedRedis.addToInbox(follower.getUid(), postId, ts);
                        followerCount++;
                    } catch (RuntimeException e) {
                        failedWrites++;
                        log.warn("feed fanout inbox write failed: source={} postId={} authorId={} followerUid={} written={} failed={}",
                                source, LogMask.id(postId), LogMask.id(authorId), LogMask.id(follower.getUid()),
                                followerCount, failedWrites);
                        throw e;
                    }
                }

                FollowCursorDTO last = followers.get(followers.size() - 1);
                Long nextCursor = last == null ? null : last.getRelationId();
                if (nextCursor == null || nextCursor <= 0 || followers.size() < FANOUT_BATCH_SIZE) {
                    break;
                }
                cursor = nextCursor;
            }
            log.info("feed fanout done: source={} postId={} authorId={} followers={} batches={} batchSize={}",
                    source, LogMask.id(postId), LogMask.id(authorId), followerCount, batches, FANOUT_BATCH_SIZE);
            return true;
        } catch (Exception e) {
            idempotentChecker.release(idempotentKey, CONSUMER_NAME);
            log.error("feed fanout failed: source={} postId={} authorId={} followers={} failedWrites={} retryReleased={}",
                    source, LogMask.id(postId), LogMask.id(authorId), followerCount, failedWrites, true, e);
            throw e;
        }
    }

    private boolean isPublicPublished(PostPublishedEvent event) {
        return event != null
                && Integer.valueOf(Post.VIS_PUBLIC).equals(event.getVisibility())
                && Integer.valueOf(Post.STATUS_PUBLISHED).equals(event.getPostStatus());
    }

}
