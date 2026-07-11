package com.offerlab.community.post.application;

import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicFollowMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityTopicNotificationTargetService {

    private static final int MAX_NOTIFICATION_TOPICS = 8;
    private static final int MAX_TOPIC_FOLLOWER_FANOUT = 1000;

    private final CommunityTopicMapper topicMapper;
    private final CommunityTopicFollowMapper topicFollowMapper;
    private final MigrationCheckService migrationCheckService;

    public List<PostPublishedEvent.TopicNotificationTarget> targetsForPost(List<Long> tagIds, Long authorId) {
        if (!migrationCheckService.communityTopicReady()) {
            return List.of();
        }
        List<Long> safeTagIds = tagIds == null ? List.of() : tagIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(20)
                .toList();
        if (safeTagIds.isEmpty()) {
            return List.of();
        }
        List<CommunityTopicPO> topics = topicMapper.selectOnlineTopicsByTagIds(safeTagIds, MAX_NOTIFICATION_TOPICS);
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        List<PostPublishedEvent.TopicNotificationTarget> targets = new ArrayList<>();
        int remainingFanout = MAX_TOPIC_FOLLOWER_FANOUT;
        for (CommunityTopicPO topic : topics) {
            if (topic == null || topic.getId() == null || remainingFanout <= 0) {
                continue;
            }
            List<Long> followerUids = topicFollowMapper.selectFollowerUidsForNotification(
                            topic.getId(), authorId, remainingFanout)
                    .stream()
                    .filter(uid -> uid != null && uid > 0)
                    .distinct()
                    .toList();
            if (followerUids.isEmpty()) {
                continue;
            }
            targets.add(PostPublishedEvent.TopicNotificationTarget.builder()
                    .topicId(topic.getId())
                    .topicSlug(topic.getSlug())
                    .topicName(topic.getTopicName())
                    .followerUids(followerUids)
                    .build());
            remainingFanout -= followerUids.size();
        }
        return targets;
    }
}
