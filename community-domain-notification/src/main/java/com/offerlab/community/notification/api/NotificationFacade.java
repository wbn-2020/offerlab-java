package com.offerlab.community.notification.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.notification.api.dto.NotificationReadAllResultDTO;
import com.offerlab.community.notification.api.dto.NotificationRealtimeStatusDTO;

import java.util.List;
import java.util.Map;

/**
 * MVP 阶段：占位实现，二期接通知体系（Kafka + Netty）
 */
public interface NotificationFacade {

    PageResult<Map<String, Object>> listNotifications(Long uid, String type, String cursor, int size);

    long getUnreadCount(Long uid);

    Map<String, Long> getUnreadCountByType(Long uid);

    NotificationRealtimeStatusDTO getRealtimeStatus(Long uid);

    void markAsRead(Long uid, List<Long> notifIds);

    NotificationReadAllResultDTO markAllAsRead(Long uid);

    void notifyLike(Long receiverUid, Long senderUid, Integer targetType, Long targetId);

    void notifyCommentLike(Long receiverUid, Long senderUid, Long postId, Long commentId);

    void notifyComment(Long receiverUid, Long senderUid, Long postId, Long commentId);

    void notifyAnswerAccepted(Long receiverUid, Long senderUid, Long postId, Long commentId,
                              Map<String, Object> content);

    void notifyDiscussionFollowComment(Long receiverUid, Long senderUid, Long postId, Long commentId);

    void notifyDiscussionFollowQualityComment(Long receiverUid, Long senderUid, Long postId, Long commentId, String action);

    void notifyFollower(Long receiverUid, Long senderUid);

    void notifyFavorite(Long receiverUid, Long senderUid, Long postId);

    void notifyMention(Long receiverUid, Long senderUid, Long postId, Long commentId);

    void notifySystem(Long receiverUid, Long targetType, Long targetId, Map<String, Object> content);

    void notifyReportReceipt(Long receiverUid, String sourceType, Long reportId, String userStatus, String targetPath);

    void notifyContactRequestReceived(Long receiverUid, Long requesterUid, Long requestId);

    void notifyContactRequestAccepted(Long requesterUid, Long receiverUid, Long requestId);

    void notifyContactRequestRejected(Long requesterUid, Long receiverUid, Long requestId);
}
