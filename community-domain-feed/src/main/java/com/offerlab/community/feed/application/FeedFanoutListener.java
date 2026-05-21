package com.offerlab.community.feed.application;

import com.offerlab.community.feed.infrastructure.FeedInboxRedis;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.user.api.UserFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 监听 PostPublishedEvent 进行写扩散
 * 第一阶段简化：所有用户都走推；最多扇出前 N 个粉丝（避免一次性大查询）；
 * 二期切到 Kafka 消费者 + 大V分流（详见 04-Feed流设计.md）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedFanoutListener {

    /** MVP 阶段单帖最多扇出粉丝数；超出靠用户主动拉取兜底 */
    private static final int MAX_FANOUT = 10000;

    private final UserFacade userFacade;
    private final FeedInboxRedis feedRedis;

    @Async
    @EventListener
    public void onPostPublished(PostPublishedEvent event) {
        try {
            Long postId = event.getPostId();
            Long authorId = event.getAuthorId();
            long ts = event.getTimestamp();

            feedRedis.addToAuthorTimeline(authorId, postId, ts);
            feedRedis.addToGlobalLatest(postId, ts);

            List<Long> followers = userFacade.getFollowerIds(authorId, 0, MAX_FANOUT);
            for (Long fuid : followers) {
                feedRedis.addToInbox(fuid, postId, ts);
            }
            log.info("fanout done: postId={} author={} fans={}", postId, authorId, followers.size());
        } catch (Exception e) {
            log.error("fanout failed: {}", event, e);
        }
    }
}
