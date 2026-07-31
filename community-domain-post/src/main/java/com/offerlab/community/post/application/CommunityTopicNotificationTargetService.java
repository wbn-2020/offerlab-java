package com.offerlab.community.post.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.post.api.event.PostPublishedEvent;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicFollowMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.CommunityTopicMapper;
import com.offerlab.community.post.infrastructure.persistence.po.CommunityTopicPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommunityTopicNotificationTargetService {

    private static final int MAX_NOTIFICATION_TOPICS = 8;
    private static final int MAX_TOPIC_FOLLOWER_FANOUT = 1000;

    private final CommunityTopicMapper topicMapper;
    private final CommunityTopicFollowMapper topicFollowMapper;
    private final MigrationCheckService migrationCheckService;
    private final ObjectMapper objectMapper;

    public List<PostPublishedEvent.TopicNotificationTarget> targetsForPost(List<Long> tagIds,
                                                                           String extJson,
                                                                           Long authorId) {
        if (!migrationCheckService.communityTopicReady()) {
            return List.of();
        }
        List<Long> safeTagIds = tagIds == null ? List.of() : tagIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(20)
                .toList();
        Map<Long, CommunityTopicPO> topicsById = new LinkedHashMap<>();
        if (!safeTagIds.isEmpty()) {
            addOnlineTopics(topicsById, topicMapper.selectOnlineTopicsByTagIds(safeTagIds, MAX_NOTIFICATION_TOPICS));
        }
        addExtensionTopics(topicsById, extJson);
        if (topicsById.isEmpty()) {
            return List.of();
        }
        List<PostPublishedEvent.TopicNotificationTarget> targets = new ArrayList<>();
        int remainingFanout = MAX_TOPIC_FOLLOWER_FANOUT;
        for (CommunityTopicPO topic : topicsById.values()) {
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

    private void addExtensionTopics(Map<Long, CommunityTopicPO> topicsById, String extJson) {
        if (extJson == null || extJson.isBlank() || topicsById.size() >= MAX_NOTIFICATION_TOPICS) {
            return;
        }
        try {
            JsonNode extension = objectMapper.readTree(extJson);
            JsonNode contextTopicId = extension.path("contextTopicId");
            if (contextTopicId.canConvertToLong()) {
                addOnlineTopic(topicsById, topicMapper.selectById(contextTopicId.asLong()));
            } else if (contextTopicId.isTextual()) {
                try {
                    addOnlineTopic(topicsById, topicMapper.selectById(Long.parseLong(contextTopicId.asText().trim())));
                } catch (NumberFormatException ignored) {
                    // Virtual topics intentionally have no numeric topic id.
                }
            }
            JsonNode topicNames = extension.path("topicNames");
            if (topicNames.isArray()) {
                for (JsonNode topicName : topicNames) {
                    if (topicsById.size() >= MAX_NOTIFICATION_TOPICS) {
                        break;
                    }
                    String name = topicName.asText("").trim();
                    if (!name.isBlank()) {
                        addOnlineTopic(topicsById, topicMapper.selectBySlugOrName(name, name));
                    }
                }
            }
        } catch (Exception ignored) {
            // Invalid optional extension metadata must not block publication.
        }
    }

    private void addOnlineTopics(Map<Long, CommunityTopicPO> topicsById, List<CommunityTopicPO> topics) {
        if (topics == null) {
            return;
        }
        for (CommunityTopicPO topic : topics) {
            if (topicsById.size() >= MAX_NOTIFICATION_TOPICS) {
                break;
            }
            addOnlineTopic(topicsById, topic);
        }
    }

    private void addOnlineTopic(Map<Long, CommunityTopicPO> topicsById, CommunityTopicPO topic) {
        if (topic != null
                && topic.getId() != null
                && Integer.valueOf(1).equals(topic.getTopicStatus())
                && !Integer.valueOf(1).equals(topic.getIsDeleted())) {
            topicsById.putIfAbsent(topic.getId(), topic);
        }
    }
}
