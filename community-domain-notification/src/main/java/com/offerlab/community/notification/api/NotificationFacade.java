package com.offerlab.community.notification.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.notification.api.dto.NotificationRealtimeStatusDTO;

import java.util.List;
import java.util.Map;

/**
 * MVP 阶段：占位实现，二期接通知体系（Kafka + Netty）
 */
public interface NotificationFacade {

    PageResult<Map<String, Object>> listNotifications(Long uid, String type, long cursor, int size);

    long getUnreadCount(Long uid);

    Map<String, Long> getUnreadCountByType(Long uid);

    NotificationRealtimeStatusDTO getRealtimeStatus(Long uid);

    void markAsRead(Long uid, List<Long> notifIds);

    void markAllAsRead(Long uid);

    void notifyLike(Long receiverUid, Long senderUid, Integer targetType, Long targetId);

    void notifyCommentLike(Long receiverUid, Long senderUid, Long postId, Long commentId);

    void notifyComment(Long receiverUid, Long senderUid, Long postId, Long commentId);

    void notifyFollower(Long receiverUid, Long senderUid);

    void notifyFavorite(Long receiverUid, Long senderUid, Long postId);

    void notifyMention(Long receiverUid, Long senderUid, Long postId, Long commentId);

    void notifySystem(Long receiverUid, Long targetType, Long targetId, Map<String, Object> content);
}
